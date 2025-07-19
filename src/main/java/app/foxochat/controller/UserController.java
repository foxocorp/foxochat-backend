package app.foxochat.controller;

import app.foxochat.constant.APIConstant;
import app.foxochat.constant.AttributeConstant;
import app.foxochat.dto.api.request.*;
import app.foxochat.dto.api.response.*;
import app.foxochat.dto.internal.MediaPresignedURLDTO;
import app.foxochat.exception.media.MediaCannotBeEmptyException;
import app.foxochat.exception.media.MediaNotFoundException;
import app.foxochat.exception.media.UploadFailedException;
import app.foxochat.exception.member.MemberNotFoundException;
import app.foxochat.exception.otp.OTPExpiredException;
import app.foxochat.exception.otp.OTPsInvalidException;
import app.foxochat.exception.user.ContactAlreadyExistException;
import app.foxochat.exception.user.ContactNotFoundException;
import app.foxochat.exception.user.UserCredentialsIsInvalidException;
import app.foxochat.exception.user.UserNotFoundException;
import app.foxochat.model.Avatar;
import app.foxochat.model.Channel;
import app.foxochat.model.Message;
import app.foxochat.model.User;
import app.foxochat.service.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RestController
@Tag(name = "Users")
@RequestMapping(value = APIConstant.USERS, produces = "application/json")
public class UserController {

    private final UserService userService;

    private final MemberService memberService;

    private final MessageService messageService;

    private final MediaService mediaService;

    private final ContactService contactService;

    public UserController(UserService userService, MemberService memberService, MessageService messageService,
                          MediaService mediaService, ContactService contactService) {
        this.userService = userService;
        this.memberService = memberService;
        this.messageService = messageService;
        this.mediaService = mediaService;
        this.contactService = contactService;
    }

    @Operation(summary = "Get me")
    @GetMapping("/@me")
    public UserDTO getMe(@RequestAttribute(value = AttributeConstant.USER) User user,
                         @RequestParam(defaultValue = "false") boolean withChannels,
                         @RequestParam(defaultValue = "false") boolean withContacts,
                         @RequestParam(defaultValue = "false") boolean withAvatar,
                         @RequestParam(defaultValue = "false") boolean withBanner
    ) {
        List<Long> channels = null;
        if (withChannels) channels = memberService.getChannelsByUserId(user.getId())
                .stream()
                .map(Channel::getId)
                .collect(Collectors.toList());

        List<Long> contacts = null;
        if (withContacts) contacts =
                contactService.findAllByUserId(user.getId()).stream().map(userContact -> userContact.getContact().getId()).toList();

        return new UserDTO(user, channels, contacts, true, withChannels, withContacts, withAvatar, withBanner);
    }

    @Operation(summary = "Get user by id")
    @GetMapping("/{id}")
    public UserDTO getById(@PathVariable long id,
                           @RequestParam(defaultValue = "false") boolean withAvatar,
                           @RequestParam(defaultValue = "false") boolean withBanner
    ) throws UserNotFoundException {
        return new UserDTO(userService.getById(id).orElseThrow(UserNotFoundException::new),
                null,
                null, false,
                false, false, withAvatar, withBanner);
    }

    @Operation(summary = "Get user by username")
    @GetMapping("/@{username}")
    public UserDTO getByUsername(@PathVariable String username,
                                 @RequestParam(defaultValue = "false") boolean withAvatar,
                                 @RequestParam(defaultValue = "false") boolean withBanner
    ) throws UserNotFoundException {
        return new UserDTO(userService.getByUsername(username).orElseThrow(UserNotFoundException::new),
                null,
                null,
                false,
                false,
                false, withAvatar, withBanner);
    }

    @Operation(summary = "Get user channels")
    @GetMapping("/@me/channels")
    public List<ChannelShortDTO> getChannels(@RequestAttribute(value = AttributeConstant.USER) User authenticatedUser,
                                        @RequestParam(defaultValue = "false") boolean withAvatar,
                                        @RequestParam(defaultValue = "false") boolean withBanner,
                                        @RequestParam(defaultValue = "false") boolean withOwner
    ) {
        return memberService.getChannelsByUserId(authenticatedUser.getId())
                .stream()
                .map(channel -> {
                    Message lastMessage = messageService.getLastByChannel(channel);
                    return new ChannelShortDTO(channel, lastMessage, withAvatar, withBanner, withOwner);
                })
                .collect(Collectors.toList());
    }

    @Operation(summary = "Edit user")
    @PatchMapping("/@me")
    public UserShortDTO edit(
            @RequestAttribute(value = AttributeConstant.USER) User authenticatedUser,
            @RequestBody UserEditDTO body
    ) throws Exception {
        authenticatedUser = userService.update(authenticatedUser, body);

        return new UserShortDTO(authenticatedUser, true, true);
    }

    @Operation(summary = "Upload avatar")
    @PutMapping("/@me/avatar")
    public MediaUploadDTO uploadAvatar(
            @RequestAttribute(value = AttributeConstant.USER) User authenticatedUser,
            @RequestBody AvatarUploadDTO avatar
    ) throws MediaNotFoundException, MediaCannotBeEmptyException, UploadFailedException {
        MediaPresignedURLDTO data = mediaService.uploadAvatar(authenticatedUser, null, avatar);

        Avatar media;
        try {
            media = (Avatar) data.getMedia().getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new UploadFailedException();
        }

        return new MediaUploadDTO(data.getUrl(), media.getId());
    }

    @Operation(summary = "Upload banner")
    @PutMapping("/@me/banner")
    public List<MediaUploadDTO> uploadBanner(
            @RequestAttribute(value = AttributeConstant.USER) User authenticatedUser,
            @RequestBody AttachmentUploadDTO attachment
    ) throws MediaCannotBeEmptyException {
        return mediaService.uploadAttachments(authenticatedUser, List.of(attachment));
    }

    @Operation(summary = "Delete")
    @DeleteMapping("/@me")
    public OkDTO delete(
            @RequestAttribute(value = AttributeConstant.USER) User user,
            @RequestBody UserDeleteDTO body
    ) throws UserCredentialsIsInvalidException {
        String password = body.getPassword();

        userService.requestDelete(user, password);

        return new OkDTO(true);
    }

    @Operation(summary = "Confirm delete")
    @PostMapping("/@me/delete-confirm")
    public OkDTO deleteConfirm(
            @RequestAttribute(value = AttributeConstant.USER) User user,
            @RequestBody OTPDTO body
    ) throws OTPExpiredException, OTPsInvalidException {
        userService.confirmDelete(user, body.getOTP());

        return new OkDTO(true);
    }

    @Operation(summary = "Get contacts")
    @GetMapping("/@me/contacts")
    public List<UserShortDTO> getContacts(
            @RequestAttribute(value = AttributeConstant.USER) User authenticatedUser,
            @RequestParam(defaultValue = "false") boolean withAvatar,
            @RequestParam(defaultValue = "false") boolean withBanner
    ) throws UserNotFoundException {
        User user = userService.getById(authenticatedUser.getId()).orElseThrow(UserNotFoundException::new);
        return user.getContacts()
                .stream()
                .map(contact -> new UserShortDTO(contact.getContact(), withAvatar,
                        withBanner))
                .collect(Collectors.toList());
    }

    @Operation(summary = "Add contact")
    @PostMapping("/{id}")
    public UserShortDTO addContact(
            @RequestAttribute(value = AttributeConstant.USER) User user,
            @PathVariable long id
    ) throws ContactAlreadyExistException {
        return new UserShortDTO(userService.addContact(user, id), true, true);
    }

    @Operation(summary = "Delete contact")
    @DeleteMapping("/{id}")
    public OkDTO deleteContact(
            @RequestAttribute(value = AttributeConstant.USER) User user,
            @PathVariable long id
    ) throws ContactNotFoundException {
        userService.deleteContact(user, id);

        return new OkDTO(true);
    }
}
