package app.foxochat.controller;

import app.foxochat.constant.APIConstant;
import app.foxochat.constant.AttributeConstant;
import app.foxochat.constant.ChannelConstant;
import app.foxochat.dto.api.request.*;
import app.foxochat.dto.api.response.*;
import app.foxochat.dto.internal.MediaPresignedURLDTO;
import app.foxochat.exception.channel.ChannelAlreadyExistException;
import app.foxochat.exception.channel.ChannelNotFoundException;
import app.foxochat.exception.media.MediaCannotBeEmptyException;
import app.foxochat.exception.media.MediaNotFoundException;
import app.foxochat.exception.media.UploadFailedException;
import app.foxochat.exception.member.MemberNotFoundException;
import app.foxochat.exception.member.MissingPermissionsException;
import app.foxochat.exception.message.MessageCannotBeEmpty;
import app.foxochat.exception.message.MessageNotFoundException;
import app.foxochat.exception.user.UserNotFoundException;
import app.foxochat.model.*;
import app.foxochat.service.ChannelService;
import app.foxochat.service.MediaService;
import app.foxochat.service.MemberService;
import app.foxochat.service.MessageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

@Slf4j
@RestController
@Tag(name = "Channels")
@RequestMapping(value = APIConstant.CHANNELS, produces = "application/json")
public class ChannelController {

    private final ChannelService channelService;

    private final MessageService messageService;

    private final MemberService memberService;

    private final MediaService mediaService;

    public ChannelController(ChannelService channelService, MessageService messageService, MemberService memberService,
                             MediaService mediaService) {
        this.channelService = channelService;
        this.messageService = messageService;
        this.memberService = memberService;
        this.mediaService = mediaService;
    }

    @Operation(summary = "Create channel")
    @PostMapping("/")
    public ChannelDTO create(
            @RequestAttribute(value = AttributeConstant.USER) User user,
            @RequestBody ChannelCreateDTO body
    ) throws ChannelAlreadyExistException, UserNotFoundException, MemberNotFoundException {
        Channel channel = channelService.add(user, 0, body);

        return new ChannelDTO(channel, null, null, null, null, true, true, true);
    }

    @Operation(summary = "Create DM channel")
    @PostMapping("/{partnerId}")
    public ChannelDTO createDM(
            @RequestAttribute(value = AttributeConstant.USER) User user,
            @RequestBody ChannelCreateDTO body, @PathVariable long partnerId
    ) throws ChannelAlreadyExistException, UserNotFoundException, MemberNotFoundException {
        Channel channel = channelService.add(user, partnerId, body);

        return new ChannelDTO(channel, null, null, null, null, true, true, true);
    }

    @Operation(summary = "Get channel by id")
    @GetMapping("/{channelId}")
    public ChannelDTO getById(
            @RequestAttribute(value = AttributeConstant.USER) User user,
            @RequestAttribute(value = AttributeConstant.CHANNEL) Channel channel,
            @PathVariable long channelId,
            @RequestParam(defaultValue = "false") boolean withAvatar,
            @RequestParam(defaultValue = "false") boolean withBanner,
            @RequestParam(defaultValue = "false") boolean withOwner
    ) throws UserNotFoundException, MemberNotFoundException {
        ChannelDTO dto = new ChannelDTO(channel, null, null, null, null, withAvatar, withBanner, withOwner);

        if (channel.getType() != ChannelConstant.Type.DM.getType()) {
            User partnerUser = channel.getMembers().stream().filter(m -> m.getUser().getId() != user.getId())
                    .findFirst().orElseThrow(UserNotFoundException::new).getUser();
            dto = new ChannelDTO(channel,
                    null,
                    partnerUser.getDisplayName(),
                    partnerUser.getUsername(),
                    partnerUser.getAvatar(), withAvatar, withBanner, withOwner);
        }

        return dto;
    }

    @Operation(summary = "Get channel by name")
    @GetMapping("/@{name}")
    public ChannelDTO getByName(
            @RequestAttribute(value = AttributeConstant.USER) User user,
            @PathVariable String name,
            @RequestParam(defaultValue = "false") boolean withAvatar,
            @RequestParam(defaultValue = "false") boolean withBanner,
            @RequestParam(defaultValue = "false") boolean withOwner
    ) throws ChannelNotFoundException, MemberNotFoundException {
        Channel channel = channelService.getByName(name);
        ChannelDTO dto = new ChannelDTO(channel, null, name, null, null, withAvatar, withBanner, withOwner);

        if (channel.getType() != ChannelConstant.Type.DM.getType()) {
            User partnerUser = channel.getMembers().stream().filter(m -> m.getUser().getId() != user.getId())
                    .findFirst().get().getUser();
            dto = new ChannelDTO(channel,
                    null,
                    partnerUser.getDisplayName(),
                    partnerUser.getUsername(),
                    partnerUser.getAvatar(), withAvatar, withBanner, withOwner);
        }

        return dto;
    }

    @Operation(summary = "Edit channel")
    @PatchMapping("/{channelId}")
    public ChannelShortDTO edit(
            @RequestAttribute(value = AttributeConstant.MEMBER) Member member,
            @RequestAttribute(value = AttributeConstant.CHANNEL) Channel channel,
            @PathVariable long channelId,
            @RequestBody ChannelEditDTO body
    ) throws Exception {
        channel = channelService.update(member, channel, body);

        return new ChannelShortDTO(channel, null, true, true, true);
    }

    @Operation(summary = "Upload icon")
    @PutMapping("/{channelId}/icon")
    public MediaUploadDTO uploadAvatar(
            @PathVariable String channelId,
            @RequestAttribute(value = AttributeConstant.CHANNEL) Channel channel,
            @RequestBody AvatarUploadDTO avatar
    ) throws MediaNotFoundException, MediaCannotBeEmptyException, UploadFailedException {
        MediaPresignedURLDTO data = mediaService.uploadAvatar(null, channel, avatar);

        Avatar media;
        try {
            media = (Avatar) data.getMedia().getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new UploadFailedException();
        }

        return new MediaUploadDTO(data.getUrl(), media.getId());
    }

    @Operation(summary = "Delete channel")
    @DeleteMapping("/{channelId}")
    public OkDTO delete(
            @RequestAttribute(value = AttributeConstant.USER) User user,
            @RequestAttribute(value = AttributeConstant.CHANNEL) Channel channel,
            @PathVariable long channelId
    ) throws Exception {
        channelService.delete(channel, user);

        return new OkDTO(true);
    }

    @Operation(summary = "Join channel")
    @PutMapping("/{channelId}/members/@me")
    public OkDTO addMember(
            @RequestAttribute(value = AttributeConstant.USER) User user,
            @RequestAttribute(value = AttributeConstant.CHANNEL) Channel channel,
            @PathVariable long channelId
    ) throws Exception {
        channelService.addMember(channel, user);

        return new OkDTO(true);
    }

    @Operation(summary = "Leave channel")
    @DeleteMapping("/{channelId}/members/@me")
    public OkDTO removeMember(
            @RequestAttribute(value = AttributeConstant.USER) User user,
            @RequestAttribute(value = AttributeConstant.CHANNEL) Channel channel,
            @PathVariable long channelId
    ) throws Exception {
        channelService.removeMember(channel, user);

        return new OkDTO(true);
    }

    @Operation(summary = "Get member")
    @GetMapping("/{channelId}/members/{memberId}")
    public MemberDTO getMember(
            @RequestAttribute(value = AttributeConstant.USER) User user,
            @RequestAttribute(value = AttributeConstant.CHANNEL) Channel channel,
            @PathVariable long channelId,
            @PathVariable String memberId,
            @RequestParam(defaultValue = "false") boolean withChannel,
            @RequestParam(defaultValue = "false") boolean withUser
    ) throws MemberNotFoundException, ExecutionException, InterruptedException {
        if (Objects.equals(memberId, "@me")) {
            memberId = String.valueOf(user.getId());
        }

        Member member = memberService.getByChannelIdAndUserId(channel.getId(), Long.parseLong(memberId)).get()
                .orElseThrow(MemberNotFoundException::new);

        return new MemberDTO(member, withChannel, withUser, false);
    }

    @Operation(summary = "Get members")
    @GetMapping("/{channelId}/members")
    public List<MemberDTO> getMembers(
            @RequestAttribute(value = AttributeConstant.CHANNEL) Channel channel,
            @PathVariable long channelId,
            @RequestParam(defaultValue = "false") boolean withChannel,
            @RequestParam(defaultValue = "false") boolean withOwner,
            @RequestParam(defaultValue = "false") boolean withUser
    ) {
        return memberService.getAllByChannelId(channel.getId()).stream()
                .map(member -> new MemberDTO(member, withChannel, withUser, withOwner))
                .toList();
    }

    @Operation(summary = "Get messages")
    @GetMapping("/{channelId}/messages")
    public List<MessageDTO> getMessages(
            @RequestAttribute(value = AttributeConstant.CHANNEL) Channel channel,
            @PathVariable long channelId,
            @RequestParam(defaultValue = "0") long before,
            @RequestParam(defaultValue = "25") int limit,
            @RequestParam(defaultValue = "false") boolean withChannel,
            @RequestParam(defaultValue = "false") boolean withAttachments,
            @RequestParam(defaultValue = "false") boolean withAuthor,
            @RequestParam(defaultValue = "false") boolean withUser
    ) {
        if (before <= 0) {
            before = System.currentTimeMillis();
        }

        if (limit <= 0) {
            limit = 25;
        }

        return messageService.getAllByChannel(before, limit, channel).stream()
                .map(message -> new MessageDTO(message, withChannel, withUser, withAuthor, withAttachments))
                .collect(Collectors.toList());
    }

    @Operation(summary = "Get message")
    @GetMapping("/{channelId}/messages/{messageId}")
    public MessageDTO getMessage(
            @RequestAttribute(value = AttributeConstant.CHANNEL) Channel channel,
            @PathVariable long channelId,
            @PathVariable long messageId,
            @RequestParam(defaultValue = "false") boolean withChannel,
            @RequestParam(defaultValue = "false") boolean withAttachments,
            @RequestParam(defaultValue = "false") boolean withAuthor,
            @RequestParam(defaultValue = "false") boolean withUser
    ) throws MessageNotFoundException {
        return new MessageDTO(messageService.getByIdAndChannel(messageId, channel), withChannel,
                withUser,
                withAuthor, withAttachments);
    }

    @Operation(summary = "Create message")
    @PostMapping("/{channelId}/messages")
    public OkDTO createMessage(
            @RequestAttribute(value = AttributeConstant.USER) User user,
            @RequestAttribute(value = AttributeConstant.CHANNEL) Channel channel,
            @PathVariable long channelId,
            @RequestBody MessageCreateDTO body
    ) throws Exception {
        if (body.getContent() == null && body.getAttachments() == null) {
            throw new MessageCannotBeEmpty();
        }

        messageService.add(channel, user, body);

        return new OkDTO(true);
    }

    @Operation(summary = "Add attachments")
    @PutMapping("/{channelId}/attachments")
    public List<MediaUploadDTO> addAttachments(
            @RequestAttribute(value = AttributeConstant.USER) User user,
            @RequestAttribute(value = AttributeConstant.CHANNEL) Channel channel,
            @PathVariable String channelId,
            @RequestBody List<AttachmentUploadDTO> attachments
    )
            throws MissingPermissionsException, MediaCannotBeEmptyException, MemberNotFoundException,
            ExecutionException, InterruptedException {
        if (attachments == null || attachments.isEmpty()) {
            throw new MediaCannotBeEmptyException();
        }

        return messageService.addAttachments(channel, user, attachments);
    }

    @Operation(summary = "Delete message")
    @DeleteMapping("/{channelId}/messages/{messageId}")
    public OkDTO deleteMessage(
            @RequestAttribute(value = AttributeConstant.MEMBER) Member member,
            @RequestAttribute(value = AttributeConstant.CHANNEL) Channel channel,
            @PathVariable long channelId,
            @PathVariable long messageId
    ) throws Exception {
        messageService.delete(messageId, member, channel);

        return new OkDTO(true);
    }

    @Operation(summary = "Edit message")
    @PatchMapping("/{channelId}/messages/{messageId}")
    public MessagesDTO editMessage(
            @RequestAttribute(value = AttributeConstant.MEMBER) Member member,
            @RequestAttribute(value = AttributeConstant.CHANNEL) Channel channel,
            @PathVariable long channelId,
            @PathVariable long messageId,
            @RequestBody MessageCreateDTO body
    ) throws Exception {
        List<Message> message = List.of(messageService.update(messageId, channel, member, body));

        return new MessagesDTO(message);
    }
}
