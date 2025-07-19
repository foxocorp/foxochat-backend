package app.foxochat.service.impl;

import app.foxochat.constant.GatewayConstant;
import app.foxochat.constant.MemberConstant;
import app.foxochat.dto.api.request.AttachmentUploadDTO;
import app.foxochat.dto.api.request.MessageCreateDTO;
import app.foxochat.dto.api.response.MediaUploadDTO;
import app.foxochat.dto.api.response.MessageDTO;
import app.foxochat.exception.media.MediaCannotBeEmptyException;
import app.foxochat.exception.member.MemberNotFoundException;
import app.foxochat.exception.member.MissingPermissionsException;
import app.foxochat.exception.message.MessageNotFoundException;
import app.foxochat.model.*;
import app.foxochat.repository.MessageRepository;
import app.foxochat.service.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Caching;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

@Slf4j
@Service
public class MessageServiceImpl implements MessageService {

    private final MessageRepository messageRepository;

    private final GatewayService gatewayService;

    private final MemberService memberService;

    private final MediaService mediaService;

    private final ChannelService channelService;

    public MessageServiceImpl(MessageRepository messageRepository, GatewayService gatewayService,
                              MemberService memberService, MediaService mediaService, ChannelService channelService) {
        this.messageRepository = messageRepository;
        this.gatewayService = gatewayService;
        this.memberService = memberService;
        this.mediaService = mediaService;
        this.channelService = channelService;
    }

    @Override
    @Caching(put = {
            @CachePut(value = "messagesByChannelId", key = "#channel.id")
    })
    public List<Message> getAllByChannel(long before, int limit, Channel channel) {
        List<Message> messagesArray = messageRepository.findAllByChannel(channel, before, limit);

        log.debug("Messages ({}, {}) in channel ({}) found successfully", limit, before, channel.getId());

        return messagesArray;
    }

    @Override
    @Caching(put = {
            @CachePut(value = "messagesByChannelId", key = "#channel.id"),
            @CachePut(value = "messagesById", key = "#id")
    })
    public Message getByIdAndChannel(long id, Channel channel) throws MessageNotFoundException {
        Message message = messageRepository.findByChannelAndId(channel, id).orElseThrow(MessageNotFoundException::new);

        log.debug("Message {} in channel {} found successfully", id, channel.getId());

        return message;
    }

    @Async
    @Override
    @Caching(put = {
            @CachePut(value = "messagesByChannelId", key = "#channel.id"),
            @CachePut(value = "messagesByUserId", key = "#user.id")
    })
    public void add(Channel channel, User user, MessageCreateDTO body) throws Exception {
        Member member = memberService.getByChannelIdAndUserId(channel.getId(), user.getId()).get()
                .orElseThrow(MemberNotFoundException::new);

        if (!member.hasAnyPermission(MemberConstant.Permissions.OWNER,
                MemberConstant.Permissions.ADMIN,
                MemberConstant.Permissions.SEND_MESSAGES))
            throw new MissingPermissionsException();

        List<Attachment> attachments = new ArrayList<>();
        if (body.getAttachments() != null) attachments = mediaService.getAttachments(user, body.getAttachments()).get();

        Message message = new Message(channel, body.getContent(), member, attachments);
        messageRepository.save(message);

        gatewayService.sendToSpecificSessions(getRecipients(channel),
                GatewayConstant.Opcode.DISPATCH.ordinal(),
                new MessageDTO(message, true, true, true, true),
                GatewayConstant.Event.MESSAGE_CREATE.getValue());
        log.debug("Message {} to channel {} created successfully", message.getId(), channel.getId());
    }

    @Override
    public List<MediaUploadDTO> addAttachments(Channel channel, User user, List<AttachmentUploadDTO> attachments)
            throws MissingPermissionsException, MediaCannotBeEmptyException, MemberNotFoundException,
            ExecutionException, InterruptedException {
        if (attachments.isEmpty()) throw new MediaCannotBeEmptyException();

        Member member = memberService.getByChannelIdAndUserId(channel.getId(), user.getId()).get()
                .orElseThrow(MemberNotFoundException::new);

        if (!member.hasAnyPermission(MemberConstant.Permissions.OWNER,
                MemberConstant.Permissions.ADMIN,
                MemberConstant.Permissions.SEND_MESSAGES))
            throw new MissingPermissionsException();

        log.debug("Successfully added attachments to message {} by user {}", channel.getId(), user.getId());
        return mediaService.uploadAttachments(user, attachments);
    }

    @Override
    public void delete(long id, Member member, Channel channel) throws Exception {
        Message message = messageRepository.findByChannelAndId(channel, id).orElseThrow(MessageNotFoundException::new);

        if (!message.isAuthor(member) && !member.hasAnyPermission(MemberConstant.Permissions.OWNER,
                MemberConstant.Permissions.ADMIN,
                MemberConstant.Permissions.MANAGE_MESSAGES))
            throw new MissingPermissionsException();

        messageRepository.delete(message);
        gatewayService.sendToSpecificSessions(getRecipients(channel),
                GatewayConstant.Opcode.DISPATCH.ordinal(),
                Map.of("id", id, "channel_id", channel.getId()),
                GatewayConstant.Event.MESSAGE_DELETE.getValue());
        log.debug("Message {} in channel {} deleted successfully", id, channel.getId());
    }

    @Override
    @Caching(evict = {
            @CacheEvict(value = "messagesByChannelId", allEntries = true),
            @CacheEvict(value = "messagesById", allEntries = true),
            @CacheEvict(value = "messagesByUserId", allEntries = true)
    })
    public Message update(long id, Channel channel, Member member, MessageCreateDTO body) throws Exception {
        Message message = messageRepository.findByChannelAndId(channel, id).orElseThrow(MessageNotFoundException::new);
        String content = body.getContent();

        if (!message.isAuthor(member)) throw new MissingPermissionsException();

        message.setContent(content);
        messageRepository.save(message);

        gatewayService.sendToSpecificSessions(getRecipients(channel),
                GatewayConstant.Opcode.DISPATCH.ordinal(),
                new MessageDTO(message, true, false, true, true),
                GatewayConstant.Event.MESSAGE_UPDATE.getValue());
        log.debug("Message {} in channel {} edited successfully", id, channel.getId());

        return message;
    }

    @Override
    @Caching(put = {
            @CachePut(value = "messagesByChannelId", key = "#channel.id"),
    })
    public Message getLastByChannel(Channel channel) {
        return messageRepository.getLastMessageByChannel(channel).orElse(null);
    }

    private List<Long> getRecipients(Channel channel) {
        return memberService.getAllByChannelId(channel.getId()).stream().map(Member::getUser).map(User::getId).toList();
    }
}
