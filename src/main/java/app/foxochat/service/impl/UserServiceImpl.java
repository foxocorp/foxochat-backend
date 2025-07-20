package app.foxochat.service.impl;

import app.foxochat.config.APIConfig;
import app.foxochat.constant.EmailConstant;
import app.foxochat.constant.GatewayConstant;
import app.foxochat.constant.OTPConstant;
import app.foxochat.constant.UserConstant;
import app.foxochat.dto.api.request.UserEditDTO;
import app.foxochat.dto.api.response.UserShortDTO;
import app.foxochat.dto.gateway.response.UserUpdateDTO;
import app.foxochat.exception.otp.OTPExpiredException;
import app.foxochat.exception.otp.OTPsInvalidException;
import app.foxochat.exception.user.*;
import app.foxochat.model.Member;
import app.foxochat.model.OTP;
import app.foxochat.model.User;
import app.foxochat.model.UserContact;
import app.foxochat.repository.UserRepository;
import app.foxochat.service.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Caching;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;

    private final EmailService emailService;

    private final OTPService otpService;

    private final MediaService mediaService;

    private final APIConfig apiConfig;

    private final GatewayService gatewayService;

    private final MemberService memberService;

    private final PasswordService passwordService;

    public UserServiceImpl(UserRepository userRepository, EmailService emailService, OTPService otpService,
                           MediaService mediaService, APIConfig apiConfig, @Lazy GatewayService gatewayService,
                           MemberService memberService, PasswordService passwordService) {
        this.userRepository = userRepository;
        this.emailService = emailService;
        this.otpService = otpService;
        this.mediaService = mediaService;
        this.apiConfig = apiConfig;
        this.gatewayService = gatewayService;
        this.memberService = memberService;
        this.passwordService = passwordService;
    }

    @Override
    @Caching(put = {
            @CachePut(value = "usersById", key = "#id")
    })
    public Optional<User> getById(long id) {
        return userRepository.findById(id);
    }

    @Override
    @Caching(put = {
            @CachePut(value = "usersByUsername", key = "#username")
    })
    public Optional<User> getByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    @Override
    @Caching(put = {
            @CachePut(value = "usersByEmail", key = "#email"),
    })
    public Optional<User> getByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    @Override
    @Caching(put = {
            @CachePut(value = "usersByUsername", key = "#user.username"),
            @CachePut(value = "usersById", key = "#user.id"),
            @CachePut(value = "usersByEmail", key = "#user.email")
    })
    public void save(User user) {
        userRepository.save(user);
    }

    @Override
    public void updateFlags(User user, UserConstant.Flags removeFlag, UserConstant.Flags addFlag) {
        user.removeFlag(removeFlag);
        user.addFlag(addFlag);
        userRepository.save(user);
    }

    @Override
    @Caching(put = {
            @CachePut(value = "usersByUsername", key = "#username"),
            @CachePut(value = "usersByEmail", key = "#email")
    })
    public User add(String username, String email, String password) throws UserCredentialsDuplicateException {
        long flags = UserConstant.Flags.AWAITING_CONFIRMATION.getBit();
        if (apiConfig.isDevelopment()) flags = UserConstant.Flags.EMAIL_VERIFIED.getBit();
        int type = UserConstant.Type.USER.getType();

        User user = new User(username, email, passwordService.hash(password), flags, type);

        try {
            userRepository.save(user);
        } catch (DataIntegrityViolationException e) {
            throw new UserCredentialsDuplicateException();
        }

        log.debug("Successfully created new user {}, {}", user.getId(), user.getUsername());
        return user;
    }

    @Override
    @Caching(evict = {
            @CacheEvict(value = "usersByUsername", allEntries = true),
            @CacheEvict(value = "usersByEmail", allEntries = true)
    })
    public User update(User user, UserEditDTO body) throws Exception {
        String username = body.getUsername();
        String displayName = body.getDisplayName();
        String bio = body.getBio();
        Long avatar = body.getAvatar();
        Long banner = body.getBanner();

        if (username != null || displayName != null || avatar != null || bio != null) {
            if (username != null) user.setUsername(username);
            if (displayName != null) user.setDisplayName(displayName);
            if (bio != null) user.setBio(bio);
            if (avatar != null) {
                if (avatar == 0) user.setAvatar(null);
                else user.setAvatar(mediaService.getAvatarById(avatar));
            }
            if (banner != null) {
                if (banner == 0) user.setBanner(null);
                else user.setBanner(mediaService.getAvatarById(banner));
            }

            gatewayService.sendToSpecificSessions(user.getContacts().stream()
                            .map(userContact -> userContact.getContact().getId()).toList(),
                    GatewayConstant.Opcode.DISPATCH.ordinal(),
                    new UserUpdateDTO(user.getId(), username, displayName, bio, -1, avatar != null ? avatar : 0,
                            banner != null ? banner : 0),
                    GatewayConstant.Event.USER_UPDATE.getValue());
        }
        if (body.getEmail() != null) changeEmail(user, body);
        if (body.getPassword() != null) changePassword(user, body);

        try {
            userRepository.save(user);
        } catch (DataIntegrityViolationException e) {
            throw new UserCredentialsDuplicateException();
        }

        log.debug("User {} edited successfully", user.getUsername());

        return user;
    }

    @Override
    public void requestDelete(User user, String password) throws UserCredentialsIsInvalidException {
        if (!passwordService.verify(password, user.getPassword()))
            throw new UserCredentialsIsInvalidException();

        sendEmail(user, EmailConstant.Type.ACCOUNT_DELETE);
        log.debug("User {} delete requested successfully", user.getUsername());
    }

    @Override
    @Caching(evict = {
            @CacheEvict(value = "usersById", allEntries = true),
            @CacheEvict(value = "usersByUsername", allEntries = true),
            @CacheEvict(value = "usersByEmail", allEntries = true)
    })
    public void confirmDelete(User user, String pathCode) throws OTPsInvalidException, OTPExpiredException {
        OTP OTP = otpService.validate(pathCode);

        userRepository.delete(user);

        log.debug("User {} deleted successfully", user.getUsername());

        if (OTP == null) return; // if dev

        otpService.delete(OTP);
    }

    @Override
    public void setStatus(long userId, int status) throws Exception {
        User user = getById(userId).orElseThrow(UserNotFoundException::new);

        user.setStatus(status);
        user.setStatusUpdatedAt(System.currentTimeMillis());
        userRepository.save(user);

        List<Long> recipients = memberService.getChannelsByUserId(user.getId()).stream()
                .flatMap(channel -> memberService.getAllByChannelId(channel.getId()).stream())
                .map(Member::getId)
                .distinct()
                .collect(Collectors.toList());

        gatewayService.sendToSpecificSessions(recipients,
                GatewayConstant.Opcode.DISPATCH.ordinal(),
                new UserUpdateDTO(userId, null, null, null, status, -1, -1),
                GatewayConstant.Event.USER_UPDATE.getValue());
        log.debug("Set user {} status {} successfully", user.getUsername(), status);
    }

    @Override
    public User addContact(User user, long id) throws ContactAlreadyExistException {
        try {
            User contact = getById(id).orElseThrow(UserNotFoundException::new);
            user.getContacts().add(new UserContact(user, contact));
            userRepository.save(user);

            gatewayService.sendToSpecificSessions(Collections.singletonList(contact.getId()),
                    GatewayConstant.Opcode.DISPATCH.ordinal(),
                    new UserShortDTO(user, true, true),
                    GatewayConstant.Event.CONTACT_ADD.getValue());
            log.debug("Successfully added contact {} to user {}", contact.getId(), user.getId());
            return contact;
        } catch (DataIntegrityViolationException e) {
            throw new ContactAlreadyExistException();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void deleteContact(User user, long id) throws ContactNotFoundException {
        try {
            User contact = getById(id).orElseThrow(UserNotFoundException::new);
            user.getContacts().remove(new UserContact(user, contact));
            userRepository.save(user);

            gatewayService.sendToSpecificSessions(Collections.singletonList(contact.getId()),
                    GatewayConstant.Opcode.DISPATCH.ordinal(),
                    Map.of("id", contact.getId()),
                    GatewayConstant.Event.CONTACT_DELETE.getValue());
            log.debug("Successfully deleted contact {} from user {}", contact.getId(), user.getId());
        } catch (DataIntegrityViolationException e) {
            throw new ContactNotFoundException();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void changeEmail(User user, UserEditDTO body) {
        user.setEmail(body.getEmail());
        user.addFlag(UserConstant.Flags.AWAITING_CONFIRMATION);

        sendEmail(user, EmailConstant.Type.EMAIL_VERIFY);
        log.debug("Sent email request to change user {} email ({} -> {})",
                user.getUsername(),
                user.getEmail(),
                body.getEmail());
    }

    private void changePassword(User user, UserEditDTO body) {
        user.setPassword(passwordService.hash(body.getPassword()));
        user.setTokenVersion(user.getTokenVersion() + 1);
        user.addFlag(UserConstant.Flags.AWAITING_CONFIRMATION);

        sendEmail(user, EmailConstant.Type.RESET_PASSWORD);
        log.debug("Sent email request to change user {} password", user.getUsername());
    }

    private void sendEmail(User user, EmailConstant.Type type) {
        String emailType = type.getValue();
        String code = otpService.generate();
        long issuedAt = System.currentTimeMillis();
        long expiresAt = issuedAt + OTPConstant.Lifetime.BASE.getValue();

        emailService.send(user.getEmail(),
                user.getId(),
                emailType,
                user.getUsername(),
                code,
                issuedAt,
                expiresAt,
                null);
    }
}
