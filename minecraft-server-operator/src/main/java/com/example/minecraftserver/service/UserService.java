package com.example.minecraftserver.service;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.minecraftserver.dto.MyDto;
import com.example.minecraftserver.dto.MyRequest;
import com.example.minecraftserver.entity.User;
import com.example.minecraftserver.exception.ErrorCode;
import com.example.minecraftserver.exception.MyException;
import com.example.minecraftserver.repository.UserRepository;
import com.example.minecraftserver.security.JwtUtil;

import lombok.RequiredArgsConstructor;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private static final String DEFAULT_USERNAME = "SuperPlushkin";
    private static final String DEFAULT_PASSWORD = "johntitor2036";
    
    private final UserRepository userRepository;
    private final RegistrationInviteService registrationInviteService;
    private final TotpService totpService;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private final JwtUtil jwtUtil;

    @Transactional
    public void register(String username, String password, String inviteToken) throws MyException {
        String normalizedUsername = normalizeUsername(username);

        MyException.throwIf(
            password == null || password.isBlank(),
            ErrorCode.INVALID_PASSWORD
        );
        MyException.throwIf(
            userRepository.existsByUsername(normalizedUsername),
            ErrorCode.USERNAME_ALREADY_EXISTS_OR_INVALID_DATA
        );

        registrationInviteService.consumeInvite(inviteToken, normalizedUsername);
        userRepository.save(
            new User(normalizedUsername, encoder.encode(password))
        );
    }

    @Transactional
    public void ensureDefaultUserExists() throws MyException {
        String normalizedUsername = normalizeUsername(DEFAULT_USERNAME);
        if (userRepository.existsByUsername(normalizedUsername)) {
            return;
        }

        userRepository.save(new User(normalizedUsername, encoder.encode(DEFAULT_PASSWORD)));
    }

    @Transactional
    public String authenticate(String login, String password, String totpCode, String minecraftUuid, String minecraftUsername) throws MyException {
        User user = requireAuthenticatedUser(login, password);

        assertTotp(user, totpCode);

        if (minecraftUuid != null && !minecraftUuid.isBlank()) {
            bindMinecraftIdentity(user, minecraftUuid, minecraftUsername);
        }
        return jwtUtil.generateToken(user.getId());
    }

    public String authenticate(String login, String password) throws MyException {
        return authenticate(login, password, null, null, null);
    }

    public boolean isTotpRequiredForLogin(String login, String password) throws MyException {
        return requireAuthenticatedUser(login, password).isTotpEnabled();
    }

    public User getUser(Long userId) throws MyException {
        return userRepository.findById(userId)
            .orElseThrow(() -> new MyException(ErrorCode.USER_NOT_EXISTS_OR_INVALID_DATA));
    }

    public void assertMinecraftIdentity(Long userId, String minecraftUuid) throws MyException {
        User user = getUser(userId);
        String normalizedUuid = normalizeMinecraftUuid(minecraftUuid);

        MyException.throwIf(
            user.getMinecraftUuid() == null || user.getMinecraftUuid().isBlank(),
            ErrorCode.MINECRAFT_NOT_LINKED
        );
        MyException.throwIf(
            !user.getMinecraftUuid().equals(normalizedUuid),
            ErrorCode.MINECRAFT_UUID_MISMATCH
        );
    }

    @Transactional
    public MyDto.UserProfile linkMinecraftIdentity(Long userId, String minecraftUuid, String minecraftUsername) throws MyException {
        User user = getUser(userId);
        attachMinecraftIdentity(user, minecraftUuid, minecraftUsername, true);
        return toProfileDto(user);
    }

    @Transactional
    public MyDto.UserProfile unlinkMinecraftIdentity(Long userId, String minecraftUuid) throws MyException {
        assertMinecraftIdentity(userId, minecraftUuid);
        User user = getUser(userId);
        user.setMinecraftUuid(null);
        user.setMinecraftUsername(null);
        return toProfileDto(user);
    }

    public MyDto.UserProfile getProfile(Long userId) throws MyException {
        return toProfileDto(getUser(userId));
    }

    @Transactional
    public MyDto.UserProfile updateProfile(Long userId, MyRequest.AccountProfileUpdate request) throws MyException {
        User user = getUser(userId);
        assertTotp(user, request.getTotpCode());

        String nextUsername = request.getUsername() != null ? normalizeUsername(request.getUsername()) : user.getUsername();
        MyException.throwIf(
            !user.getUsername().equals(nextUsername) && userRepository.existsByUsername(nextUsername),
            ErrorCode.USERNAME_ALREADY_EXISTS_OR_INVALID_DATA
        );

        user.setUsername(nextUsername);
        return toProfileDto(user);
    }

    @Transactional
    public MyDto.UserProfile updatePassword(Long userId, MyRequest.AccountPasswordUpdate request) throws MyException {
        User user = getUser(userId);
        assertTotp(user, request.getTotpCode());

        String newPassword = request.getNewPassword();
        MyException.throwIf(
            newPassword == null || newPassword.isBlank(),
            ErrorCode.INVALID_PASSWORD
        );

        user.setPasswordHash(encoder.encode(newPassword));
        return toProfileDto(user);
    }

    @Transactional
    public MyDto.UserProfile updateMinecraftIdentity(Long userId, MyRequest.AccountMinecraftUpdate request) throws MyException {
        User user = getUser(userId);
        assertTotp(user, request.getTotpCode());
        attachMinecraftIdentity(user, request.getMinecraftUuid(), request.getMinecraftUsername(), true);
        return toProfileDto(user);
    }

    @Transactional
    public MyDto.TotpSetupResponse beginTotpSetup(Long userId) throws MyException {
        User user = getUser(userId);
        String secret = totpService.generateSecret();
        user.setTotpSecret(secret);
        user.setTotpEnabled(false);

        String otpAuthUri = totpService.buildOtpAuthUri(user.getUsername(), secret);
        return new MyDto.TotpSetupResponse(
            false,
            secret,
            totpService.generateQrCodeDataUrl(otpAuthUri),
            secret
        );
    }

    @Transactional
    public MyDto.UserProfile enableTotp(Long userId, String code) throws MyException {
        User user = getUser(userId);
        MyException.throwIf(
            user.getTotpSecret() == null || user.getTotpSecret().isBlank(),
            ErrorCode.TOTP_SETUP_REQUIRED
        );
        MyException.throwIf(
            !totpService.isCodeValid(user.getTotpSecret(), code),
            ErrorCode.TOTP_CODE_INVALID
        );

        user.setTotpEnabled(true);
        return toProfileDto(user);
    }

    @Transactional
    public MyDto.UserProfile disableTotp(Long userId, String code) throws MyException {
        User user = getUser(userId);
        MyException.throwIf(
            !user.isTotpEnabled(),
            ErrorCode.TOTP_ALREADY_DISABLED 
        );
        MyException.throwIf(
            !totpService.isCodeValid(user.getTotpSecret(), code),
            ErrorCode.TOTP_CODE_INVALID
        );

        user.setTotpEnabled(false);
        user.setTotpSecret(null);
        return toProfileDto(user);
    }

    private void bindMinecraftIdentity(User user, String minecraftUuid, String minecraftUsername) throws MyException {
        attachMinecraftIdentity(user, minecraftUuid, minecraftUsername, false);
    }

    private void attachMinecraftIdentity(User user, String minecraftUuid, String minecraftUsername, boolean allowReplace) throws MyException {
        String normalizedUuid = normalizeMinecraftUuid(minecraftUuid);
        String normalizedUsername = normalizeMinecraftUsername(minecraftUsername);

        userRepository.findByMinecraftUuid(normalizedUuid)
            .filter(existingUser -> !existingUser.getId().equals(user.getId()))
            .ifPresent(existingUser -> {
                throw new MyException(ErrorCode.MINECRAFT_ALREADY_LINKED);
            });

        if (user.getMinecraftUuid() == null || user.getMinecraftUuid().isBlank()) {
            user.setMinecraftUuid(normalizedUuid);
        } else if (allowReplace) {
            user.setMinecraftUuid(normalizedUuid);
        } else if (!user.getMinecraftUuid().equals(normalizedUuid)) {
            throw new MyException(ErrorCode.MINECRAFT_NOT_LINKED_TO_USER);
        }

        if (normalizedUsername != null) {
            user.setMinecraftUsername(normalizedUsername);
        }
    }

    private MyDto.UserProfile toProfileDto(User user) {
        return new MyDto.UserProfile(
            user.getId(),
            user.getUsername(),
            user.isTotpEnabled(),
            user.getMinecraftUuid(),
            user.getMinecraftUsername(),
            user.getMinecraftUuid() != null && !user.getMinecraftUuid().isBlank(),
            user.getCreatedAt()
        );
    }

    public String resolveCanonicalUsername(String login) throws MyException {
        return findByLogin(login)
            .map(User::getUsername)
            .orElseThrow(() -> 
                new MyException(ErrorCode.USER_NOT_EXISTS_OR_INVALID_DATA)
            );
    }

    public Long resolveUserIdByUsername(String username) throws MyException {
        return findByLogin(username)
            .map(User::getId)
            .orElseThrow(() -> 
                new MyException(ErrorCode.USER_NOT_EXISTS_OR_INVALID_DATA)
            );
    }

    private java.util.Optional<User> findByLogin(String login) throws MyException {
        MyException.throwIf(
            login == null || login.isBlank(),
            ErrorCode.USER_NOT_EXISTS_OR_INVALID_DATA
        );
        return userRepository.findByUsername(login.trim());
    }

    private User requireAuthenticatedUser(String login, String password) throws MyException {
        User user = findByLogin(login)
            .orElseThrow(() -> 
                new MyException(ErrorCode.USER_NOT_EXISTS_OR_INVALID_DATA)
            );

        MyException.throwIf(
            !encoder.matches(password, user.getPasswordHash()),
            ErrorCode.USER_NOT_EXISTS_OR_INVALID_DATA
        );
        return user;
    }

    private void assertTotp(User user, String totpCode) throws MyException {
        if (!user.isTotpEnabled()) return;

        MyException.throwIf(
            !totpService.isCodeValid(user.getTotpSecret(), totpCode),
            ErrorCode.TOTP_CONFIRMATION_FAILED
        );
    }

    private String normalizeUsername(String username) throws MyException {
        MyException.throwIf(
            username == null || username.isBlank(),
            ErrorCode.USERNAME_ALREADY_EXISTS_OR_INVALID_DATA
        );
        String normalized = username.trim();
        MyException.throwIf(
            !normalized.matches("^[a-zA-Z0-9_-]{3,32}$"),
            ErrorCode.USERNAME_ALREADY_EXISTS_OR_INVALID_DATA
        );
        return normalized;
    }

    private String normalizeMinecraftUuid(String minecraftUuid) throws MyException {
        try {
            return UUID.fromString(minecraftUuid.trim()).toString();
        } catch (Exception ex) {
            throw new MyException(ErrorCode.INVALID_MINECRAFT_UUID);
        }
    }

    private String normalizeMinecraftUsername(String minecraftUsername) {
        if (minecraftUsername == null || minecraftUsername.isBlank()) {
            return null;
        }
        return minecraftUsername.trim();
    }
}