package app.foxochat.service.impl;

import app.foxochat.constant.ChannelConstant;
import app.foxochat.constant.GatewayConstant;
import app.foxochat.constant.MemberConstant;
import app.foxochat.dto.api.request.ChannelCreateDTO;
import app.foxochat.dto.api.request.ChannelEditDTO;
import app.foxochat.dto.api.response.MemberDTO;
import app.foxochat.dto.gateway.response.ChannelUpdateDTO;
import app.foxochat.exception.channel.ChannelAlreadyExistException;
import app.foxochat.exception.channel.ChannelNotFoundException;
import app.foxochat.exception.media.MediaNotFoundException;
import app.foxochat.exception.media.UploadFailedException;
import app.foxochat.exception.member.MemberAlreadyExistException;
import app.foxochat.exception.member.MemberNotFoundException;
import app.foxochat.exception.member.MissingPermissionsException;
import app.foxochat.exception.user.UserNotFoundException;
import app.foxochat.model.Channel;
import app.foxochat.model.Member;
import app.foxochat.model.User;
import app.foxochat.repository.ChannelRepository;
import app.foxochat.service.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Caching;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ChannelServiceImpl implements ChannelService {

    private final ChannelRepository channelRepository;

    private final MemberService memberService;

    private final GatewayService gatewayService;

    private final MediaService mediaService;

    private final UserService userService;

    public ChannelServiceImpl(ChannelRepository channelRepository, MemberService memberService,
                              @Lazy GatewayService gatewayService, MediaService mediaService, UserService userService) {
        this.channelRepository = channelRepository;
        this.memberService = memberService;
        this.gatewayService = gatewayService;
        this.mediaService = mediaService;
        this.userService = userService;
    }

    @Override
    @Caching(put = {
            @CachePut(value = "channelsByUserId", key = "#user.id"),
            @CachePut(value = "channelsByName", key = "#body.name")
    })
    public Channel add(User user, long partnerId, ChannelCreateDTO body)
            throws ChannelAlreadyExistException, UserNotFoundException {
        Channel channel;
        long isPublic = 0;

        if (body.isPublic() && body.getType() != ChannelConstant.Type.DM.getType())
            isPublic = ChannelConstant.Flags.PUBLIC.getBit();

        boolean isDM = partnerId != 0 && body.getType() == ChannelConstant.Type.DM.getType();

        User partner = null;
        if (isDM) {
            partner = userService.getById(partnerId).orElseThrow(UserNotFoundException::new);
        }

        try {
            channel = new Channel(body.getDisplayName(), body.getName(), isPublic, body.getType());
            channelRepository.save(channel);
        } catch (DataIntegrityViolationException e) {
            throw new ChannelAlreadyExistException();
        }

        if (isDM) {
            Member partnerMember = new Member(partner, channel, MemberConstant.Permissions.OWNER.getBit());
            memberService.add(partnerMember);
        }

        Member member = new Member(user, channel, MemberConstant.Permissions.OWNER.getBit());
        memberService.add(member);

        log.debug("Channel ({}) by user ({}) created successfully", channel.getName(), user.getUsername());
        return channel;
    }

    @Async
    @Override
    @Caching(put = {
            @CachePut(value = "channelsById", key = "#id")
    })
    public CompletableFuture<Channel> getById(long id) throws ChannelNotFoundException {
        return CompletableFuture.completedFuture(channelRepository.findById(id)
                .orElseThrow(ChannelNotFoundException::new));
    }

    @Override
    @Caching(put = {
            @CachePut(value = "channelsByName", key = "#name")
    })
    public Channel getByName(String name) throws ChannelNotFoundException {
        Channel channel = channelRepository.findByName(name).orElseThrow(ChannelNotFoundException::new);
        if (channel.hasFlag(ChannelConstant.Flags.PUBLIC)) return channel;
        throw new ChannelNotFoundException();
    }

    @Override
    @Caching(evict = {
            @CacheEvict(value = "channelsById", allEntries = true),
            @CacheEvict(value = "channelsByName", allEntries = true)
    })
    public Channel update(Member member, Channel channel, ChannelEditDTO body) throws Exception {
        if (!member.hasAnyPermission(MemberConstant.Permissions.OWNER,
                MemberConstant.Permissions.ADMIN,
                MemberConstant.Permissions.MANAGE_MESSAGES))
            throw new MissingPermissionsException();

        if (channel.getType() == ChannelConstant.Type.DM.getType()) throw new ChannelNotFoundException();

        String name = body.getName();
        String displayName = body.getDisplayName();
        Long avatar = body.getAvatar();
        Long banner = body.getBanner();
        Boolean isPublic = body.getIsPublic();

        try {
            if (name != null) channel.setName(name);
            if (displayName != null) channel.setDisplayName(body.getDisplayName());
            if (avatar != null) {
                if (avatar == 0) channel.setAvatar(null);
                else channel.setAvatar(mediaService.getAvatarById(avatar));
            }
            if (banner != null) {
                if (banner == 0) channel.setBanner(null);
                else channel.setBanner(mediaService.getAvatarById(banner));
            }
            if (isPublic != null) {
                if (isPublic) channel.addFlag(ChannelConstant.Flags.PUBLIC);
                else channel.removeFlag(ChannelConstant.Flags.PUBLIC);
            }

            channelRepository.save(channel);
        } catch (DataIntegrityViolationException e) {
            throw new ChannelAlreadyExistException();
        } catch (MediaNotFoundException e) {
            throw new UploadFailedException();
        }

        gatewayService.sendToSpecificSessions(getRecipients(channel),
                GatewayConstant.Opcode.DISPATCH.ordinal(),
                new ChannelUpdateDTO(channel.getId(), displayName, name, 0, avatar != null ? avatar : 0),
                GatewayConstant.Event.CHANNEL_UPDATE.getValue());
        log.debug("Channel ({}) edited successfully", channel.getName());
        return channel;
    }

    @Override
    public void delete(Channel channel, User user) throws Exception {
        Member member = memberService.getByChannelIdAndUserId(channel.getId(), user.getId()).get()
                .orElseThrow(MemberNotFoundException::new);

        if (!member.hasAnyPermission(MemberConstant.Permissions.OWNER, MemberConstant.Permissions.ADMIN))
            throw new MissingPermissionsException();

        channelRepository.delete(channel);
        gatewayService.sendToSpecificSessions(getRecipients(channel),
                GatewayConstant.Opcode.DISPATCH.ordinal(),
                Map.of("id", channel.getId()),
                GatewayConstant.Event.CHANNEL_DELETE.getValue());
        log.debug("Channel ({}) deleted successfully", channel.getName());
    }

    @Override
    public void addMember(Channel channel, User user) throws Exception {
        if (memberService.getByChannelIdAndUserId(channel.getId(), user.getId()).get().isPresent())
            throw new MemberAlreadyExistException();

        if (channel.getType() == ChannelConstant.Type.DM.getType()) throw new ChannelNotFoundException();

        Member member = new Member(user, channel, 0);
        member.setPermissions(MemberConstant.Permissions.ATTACH_FILES, MemberConstant.Permissions.SEND_MESSAGES);
        log.debug("Member ({}) joined channel ({}) successfully", member.getUser().getUsername(), channel.getName());
        gatewayService.sendToSpecificSessions(getRecipients(channel),
                GatewayConstant.Opcode.DISPATCH.ordinal(),
                new MemberDTO(member, true, false, false),
                GatewayConstant.Event.MEMBER_ADD.getValue());
        memberService.add(member);
    }

    @Override
    public void removeMember(Channel channel, User user) throws Exception {
        Member member = memberService.getByChannelIdAndUserId(channel.getId(), user.getId()).get()
                .orElseThrow(MemberNotFoundException::new);

        if (channel.getType() == ChannelConstant.Type.DM.getType()) throw new ChannelNotFoundException();

        memberService.delete(member);
        gatewayService.sendToSpecificSessions(getRecipients(channel),
                GatewayConstant.Opcode.DISPATCH.ordinal(),
                new MemberDTO(member, true, false, false),
                GatewayConstant.Event.MEMBER_REMOVE.getValue());
        log.debug("Member ({}) left channel ({}) successfully", member.getUser().getUsername(), channel.getName());
    }

    private List<Long> getRecipients(Channel channel) {
        Optional<Channel> optChannel = channelRepository.findById(channel.getId());

        if (optChannel.isPresent()) channel = optChannel.get();

        return channel.getMembers().stream().map(Member::getUser).map(User::getId).collect(Collectors.toList());
    }
}
