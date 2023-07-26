package com.sporthustle.hustle.src.user;

import static com.sporthustle.hustle.common.consts.Constants.*;

import com.sporthustle.hustle.common.exception.BaseException;
import com.sporthustle.hustle.common.exception.ErrorCode;
import com.sporthustle.hustle.common.jwt.JwtTokenProvider;
import com.sporthustle.hustle.common.jwt.model.TokenInfo;
import com.sporthustle.hustle.src.user.entity.Gender;
import com.sporthustle.hustle.src.user.entity.User;
import com.sporthustle.hustle.src.user.model.*;
import java.security.SecureRandom;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class UserService {

  private final JwtTokenProvider jwtTokenProvider;
  private final PasswordEncoder passwordEncoder;
  private final UserRepository userRepository;

  public JoinRes join(JoinReq joinReq) {
    if (userRepository.existsByEmail(joinReq.getEmail())) {
      throw new BaseException(ErrorCode.ALREADY_EXIST_USER);
    }
    User user =
        User.builder()
            .email(joinReq.getEmail())
            .password(passwordEncoder.encode(joinReq.getPassword()))
            .name(joinReq.getName())
            .birth(joinReq.getBirth())
            .gender(Gender.valueOf(joinReq.getGender()))
            .roles(List.of(joinReq.getRoles()))
            .build();
    userRepository.save(user);
    return JoinRes.builder().message("회원가입 완료하였습니다.").build();
  }

  public LoginRes login(LoginReq loginReq) {
    User findUser =
        userRepository
            .findByEmail(loginReq.getEmail())
            .orElseThrow(() -> new BaseException(ErrorCode.USER_NOT_FOUND));
    if (!passwordEncoder.matches(loginReq.getPassword(), findUser.getPassword())) {
      throw new BaseException(ErrorCode.INVALID_PASSWORD);
    }
    TokenInfo tokenInfo = jwtTokenProvider.createToken(loginReq.getEmail(), loginReq.getRoles());
    findUser.insertRefreshToken(tokenInfo.getRefreshToken());
    userRepository.save(findUser);
    return LoginRes.builder().tokenInfo(tokenInfo).build();
  }

  @Transactional(readOnly = true)
  public SearchUserIdRes searchUserId(SearchUserIdReq searchUserIdReq) {
    User findUser =
        userRepository
            .findByNameAndBirth(searchUserIdReq.getName(), searchUserIdReq.getBirth())
            .orElseThrow(() -> new BaseException(ErrorCode.USER_NOT_FOUND));
    return SearchUserIdRes.builder().userId(findUser.getEmail()).build();
  }

  public ClearUserPwdRes clearUserPwd(ClearUserPwdReq clearUserPwdReq) {
    User user =
        userRepository
            .findByNameAndBirthAndEmail(
                clearUserPwdReq.getName(), clearUserPwdReq.getBirth(), clearUserPwdReq.getUserId())
            .orElseThrow(() -> new BaseException(ErrorCode.USER_NOT_FOUND));
    String tmpPwd = passwordEncoder.encode(generateTemporaryPwd());
    user.changePassword(tmpPwd);
    return ClearUserPwdRes.builder().message("비밀번호가 초기화 되었습니다.").build();
  }

  private String generateTemporaryPwd() {
    SecureRandom random = new SecureRandom();
    return random
        .ints(TEMPORARY_PASSWORD_LENGTH, RANDOM_NUM_ORIGIN, RANDOM_NUM_BOUND + 1)
        .mapToObj(i -> String.valueOf((char) i))
        .collect(Collectors.joining());
  }
}
