package lms.itcluster.confassistant.service.impl;

import lms.itcluster.confassistant.dto.*;
import lms.itcluster.confassistant.entity.User;
import lms.itcluster.confassistant.exception.ForbiddenAccessException;
import lms.itcluster.confassistant.exception.NoSuchEntityException;
import lms.itcluster.confassistant.exception.UserAlreadyExistException;
import lms.itcluster.confassistant.mapper.Mapper;
import lms.itcluster.confassistant.model.CurrentUser;
import lms.itcluster.confassistant.repository.UserRepository;
import lms.itcluster.confassistant.service.EmailService;
import lms.itcluster.confassistant.service.ImageStorageService;
import lms.itcluster.confassistant.service.StaticDataService;
import lms.itcluster.confassistant.service.UserService;
import lms.itcluster.confassistant.util.SecurityUtil;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.exception.ConstraintViolationException;
import org.modelmapper.ModelMapper;
import org.modelmapper.TypeToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationAdapter;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.lang.reflect.Type;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
public class UserServiceImpl implements UserService, UserDetailsService {
    private static final Logger LOGGER = LoggerFactory.getLogger(UserServiceImpl.class);

    @Autowired
    private ImageStorageService imageStorageService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    @Qualifier("userLoginMapper")
    private Mapper<User, UserDTO> mapper;

    @Autowired
    @Qualifier("speakerMapper")
    private Mapper<User, SpeakerDTO> speakerMapper;

    @Autowired
    @Qualifier("signUpMapper")
    private Mapper<User, SignUpDTO> signUpMapper;

    @Autowired
    @Qualifier("editProfileMapper")
    private Mapper<User, EditProfileDTO> editProfileMapper;

    @Autowired
    @Qualifier("editContactMapper")
    private Mapper<User, EditContactsDTO> editContactsMapper;

    @Autowired
    private EmailService emailService;

    @Autowired
    private StaticDataService staticDataService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Value("${remove.not.completed.profiles.interval}")
    private int removeNotCompletedProfilesInterval;


    @Override
    public User findById(Long id) {
        return userRepository.findById(id).orElseThrow(()
                -> new NoSuchEntityException(String.format("User with id - %d not found.", id)));
    }

    @Override
    public UserDTO getUserDtoById(Long id) {
        return mapper.toDto(findById(id));
    }

    private User findByEmail(String email) {
        return Optional.ofNullable(userRepository.findByEmail(email)).orElseThrow(()
                -> new NoSuchEntityException(String.format("User with email - %s not found.", email)));
    }

    @Override
    @Transactional
    public void createNewUserAsGuest(SignUpDTO signUpDTO) {
        User user = signUpMapper.toEntity(signUpDTO);
        user = userRepository.save(user);
        authenticateUserIfTransactionSuccess(user);
        String link = "Please follow the link - http://localhost:8080/active/" + user.getActiveCode();
        emailService.sendMessage(user.getEmail(), "Active Profile on Conference Assistant", link);
    }

    private void authenticateUserIfTransactionSuccess(final User user) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronizationAdapter() {
            @Override
            public void afterCommit() {
                SecurityUtil.authentificateWithRememberMe(user);
            }
        });
    }

    @Override
    public void deleteUser(long id) {
        Optional<User> optionalUser = userRepository.findById(id);
        if (optionalUser.isPresent()) {
            User user = optionalUser.get();
            user.setDeleted(true);
            userRepository.save(user);
        }
    }

    @Override
    public List<UserDTO> getAllUsers() {
        List<User> users = userRepository.findAll().stream().filter(f -> !f.getDeleted()).collect(Collectors.toList());
        Type listType = new TypeToken<List<UserDTO>>() {
        }.getType();
        ModelMapper modelMapper = new ModelMapper();
        List<UserDTO> userDTOS = modelMapper.map(users, listType);
        return userDTOS;
    }


    @Override
    @Transactional
    public void updateUser(UserDTO userDTO, MultipartFile photo) throws IOException {
        Optional<User> optionalUser = Optional.of(userRepository.findByEmail(userDTO.getEmail()));
        if (optionalUser.isPresent()) {
            User dbUser = optionalUser.get();
            if (dbUser.getPassword() != userDTO.getPassword()) {
                userDTO.setPassword(passwordEncoder.encode(userDTO.getPassword()));
            }
            if ((dbUser != null) && (userDTO.getUserId() != dbUser.getUserId())) {
                throw new UserAlreadyExistException("User with this email is already exist: ");
            }
            User realUser = mapper.toEntity(userDTO);

            Optional<String> newCoverPhoto = imageStorageService.saveAndReturnImageLink(photo);
            String oldCoverPhoto = realUser.getPhoto();
            realUser.setPhoto(newCoverPhoto.orElse(oldCoverPhoto));

            userRepository.save(realUser);
            newCoverPhoto.ifPresent(s -> removeCoverPhotoIfTransactionSuccess(oldCoverPhoto));
        }
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(username);
        if (user != null && !user.getDeleted()) {
            return new CurrentUser(user);
        } else {
            throw new UsernameNotFoundException("Profile not found by email " + username);
        }
    }

    @Override
    @Transactional
    public void addNewUserByAdmin(UserDTO userDTO, MultipartFile photo) throws IOException {
        User existingUserFromDb = userRepository.findByEmail(userDTO.getEmail());
        if (existingUserFromDb != null) {
            throw new UserAlreadyExistException("User with this email is already exist: " + userDTO.getEmail());
        }
        String pass = userDTO.getPassword();
        userDTO.setPassword(passwordEncoder.encode(userDTO.getPassword()));
        User user = mapper.toEntity(userDTO);

        Optional<String> newCoverPhoto = imageStorageService.saveAndReturnImageLink(photo);
        user.setPhoto(newCoverPhoto.orElse(null));

        user.setActive(true);
        userRepository.save(user);
        emailService.sendMessage(user.getEmail(), "Administrator has created account for you on Conference Assistant",
                "You have been successfully registered on Conference Assistant. \n" +
                        "Login: " + user.getEmail() + "\n" +
                        "Password: " + pass);
    }

    @Override
    public SpeakerDTO getSpeakerById(Long id) {
        return speakerMapper.toDto(findById(id));
    }

    @Override
    @Transactional
    public void updateSpeaker(EditProfileDTO editProfileDTO, MultipartFile photo) throws IOException {
        User speaker = editProfileMapper.toEntity(editProfileDTO);

        Optional<String> newCoverPhoto = imageStorageService.saveAndReturnImageLink(photo);
        String oldCoverPhoto = speaker.getPhoto();
        speaker.setPhoto(newCoverPhoto.orElse(oldCoverPhoto));

        userRepository.save(speaker);
        newCoverPhoto.ifPresent(s -> removeCoverPhotoIfTransactionSuccess(oldCoverPhoto));
    }


    private void removeCoverPhotoIfTransactionSuccess(final String photo) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronizationAdapter() {
            @Override
            public void afterCommit() {
                imageStorageService.removeOldImage(photo);
            }
        });
    }

    @Override
    @Transactional
    public boolean createActivationCodeForConfirmEmail(EditContactsDTO editContactsDTO) {
        User user = findById(editContactsDTO.getId());
        String activationCode = UUID.randomUUID().toString();
        user.setActiveCode(activationCode);
        String link = "Please follow the link - http://localhost:8080/change/email/" + user.getActiveCode();
        emailService.sendMessage(editContactsDTO.getEmail(), "Change email address on Conference Assistant", link);
        userRepository.save(user);
        staticDataService.addUpdatedEmail(user.getUserId(), editContactsDTO.getEmail());
        return true;
    }

    @Override
    public UserDTO completeRegistration(String code, Long userID) {
        User user = userRepository.findByActiveCode(code);
        if (user == null) {
            throw new NoSuchEntityException(String.format("User with id: %d no found by activation code %s", userID, code));
        }
        if (!user.getUserId().equals(userID)) {
            throw new ForbiddenAccessException(String.format("Activation code %s don't belong to this User id: %d", code, userID));
        }
        user.setActiveCode(null);
        userRepository.save(user);
        return mapper.toDto(user);
    }

    @Override
    @Transactional
    public UserDTO findByActivationCodeAndSaveIfValid(String code, Long currentUserId) {
        User user = userRepository.findByActiveCode(code);
        if (user == null) {
            throw new NoSuchEntityException(String.format("User with id: %d no found by activation code %s", currentUserId, code));
        }
        if (!user.getUserId().equals(currentUserId)) {
            throw new ForbiddenAccessException(String.format("Activation code %s don't belong to this User id: %d", code, currentUserId));
        }

        String newEmail = staticDataService.getUpdatedEmail(currentUserId);
        user.setEmail(newEmail);
        user.setActiveCode(null);
        userRepository.save(user);
        removeTempEmailIfTransactionSuccess(currentUserId);
        return mapper.toDto(user);
    }

    private void removeTempEmailIfTransactionSuccess(final Long userId) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronizationAdapter() {
            @Override
            public void afterCommit() {
                staticDataService.removeUpdatedEmail(userId);
            }
        });
    }

    @Override
    @Transactional
    public boolean updatePassword(EditPasswordDTO editPasswordDTO) {
        User user = findById(editPasswordDTO.getId());
        user.setPassword(passwordEncoder.encode(editPasswordDTO.getPassword()));
        userRepository.save(user);
        return true;
    }

    @Override
    public EditProfileDTO getEditProfileDto(Long userId) {
        return editProfileMapper.toDto(findById(userId));
    }

    @Scheduled(cron = "0 01 00 * * *")
    @Transactional
    public void removeNotActiveProfiles() {
        LocalDate date = LocalDate.now().minusDays(removeNotCompletedProfilesInterval);
        userRepository.deleteUserByIsActiveAndCreatedLessThan(false, date);
        for (Map.Entry<Long, String> user : staticDataService.getEmailMap().entrySet()) {
            if (!userRepository.existsById(user.getKey())) {
                staticDataService.removeUpdatedEmail(user.getKey());
            }
        }
        LOGGER.info("Removed all non active user");
    }
}
