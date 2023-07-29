package com.sporthustle.hustle.common.jwt;

import static com.sporthustle.hustle.common.consts.Constants.*;

import com.sporthustle.hustle.common.jwt.model.TokenInfo;
import io.jsonwebtoken.*;
import java.util.Date;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtTokenProvider {
  @Value("${jwt.secret.key}")
  private String secretKey;

  private final UserDetailsService userDetailsService;

  private String createAccessToken(
      String userPk, List<String> roles, Date issueAt, Date accessTokenExpiredIn) {
    return Jwts.builder()
        .setSubject(userPk)
        .setIssuedAt(issueAt)
        .claim(TOKEN_TYPE, ACCESS_TOKEN)
        .claim(TOKEN_ROLE, roles)
        .setExpiration(accessTokenExpiredIn)
        .signWith(SignatureAlgorithm.HS256, secretKey)
        .compact();
  }

  private String createRefreshToken(String userPk, Date issueAt, Date accessTokenExpiredIn) {
    return Jwts.builder()
        .setSubject(userPk)
        .setIssuedAt(issueAt)
        .claim(TOKEN_TYPE, REFRESH_TOKEN)
        .setExpiration(accessTokenExpiredIn)
        .signWith(SignatureAlgorithm.HS256, secretKey)
        .compact();
  }

  public TokenInfo createToken(String userPk, List<String> roles) {
    Date now = new Date();
    Date accessTokenExpiredIn = new Date(now.getTime() * TOKEN_VALID_TIME);
    String accessToken = createAccessToken(userPk, roles, now, accessTokenExpiredIn);
    String refreshToken = createRefreshToken(userPk, now, accessTokenExpiredIn);
    return TokenInfo.builder().accessToken(accessToken).refreshToken(refreshToken).build();
  }

  public Authentication getAuthentication(String token) {
    UserDetails userDetails = userDetailsService.loadUserByUsername(this.getUserPk(token));
    return new UsernamePasswordAuthenticationToken(userDetails, "", userDetails.getAuthorities());
  }

  public String getUserPk(String token) {
    return Jwts.parser().setSigningKey(secretKey).parseClaimsJws(token).getBody().getSubject();
  }

  public String resolveToken(HttpServletRequest request) {
    String token = request.getHeader("Authorization");
    return refineToken(token);
  }

  private String refineToken(String token) {
    if (token != null && token.substring(0, 6).equals("Bearer")) {
      token = token.substring(7);
    }
    return token;
  }

  public boolean validateToken(String jwtToken) {
    try {
      Jws<Claims> claims = Jwts.parser().setSigningKey(secretKey).parseClaimsJws(jwtToken);
      return !claims.getBody().getExpiration().before(new Date());
    } catch (SecurityException | MalformedJwtException e) {
      log.info("Invalid JWT Token", e);
    } catch (ExpiredJwtException e) {
      log.info("Expired JWT Token", e);
    } catch (UnsupportedJwtException e) {
      log.info("Unsupported JWT Token", e);
    } catch (IllegalArgumentException e) {
      log.info("JWT claims string is empty.", e);
    }
    return false;
  }
}