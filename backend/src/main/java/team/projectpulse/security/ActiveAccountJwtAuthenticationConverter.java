package team.projectpulse.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.oauth2.jwt.Jwt;
import team.projectpulse.user.PeerEvaluationUser;
import team.projectpulse.user.UserRepository;

/**
 * Turns a verified JWT into an authentication only while the account it names is still active
 * (FR-SEC-active-account).
 *
 * <p>A JWT stays cryptographically valid for its whole lifetime, so on its own it would keep a deactivated
 * account working until it expired, up to two hours. This converter loads the user by the token's {@code userId}
 * claim on every request. When the account is disabled, or no longer exists, it throws {@link DisabledException}:
 * the bearer entry point answers {@code 401}, and the SPA's {@code 401} handling returns the user to login, where
 * {@code MyUserPrincipal.isEnabled} refuses her. Otherwise it hands the token to the wrapped converter, so the
 * authorities and the {@code Jwt} principal are exactly what they were before this check existed.
 *
 * <p>The cost is one primary-key lookup per authenticated request. It covers deactivation only: a leaked token of
 * an active account is still valid until it expires (TD-jwt-revocation).
 */
public class ActiveAccountJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final Converter<Jwt, ? extends AbstractAuthenticationToken> delegate;
    private final UserRepository userRepository;


    public ActiveAccountJwtAuthenticationConverter(Converter<Jwt, ? extends AbstractAuthenticationToken> delegate, UserRepository userRepository) {
        this.delegate = delegate;
        this.userRepository = userRepository;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Integer userId = readUserId(jwt);
        boolean active = userId != null && this.userRepository.findById(userId)
                .map(PeerEvaluationUser::isEnabled)
                .orElse(false);
        if (!active) {
            throw new DisabledException("This account is deactivated or no longer exists.");
        }
        return this.delegate.convert(jwt);
    }

    private static Integer readUserId(Jwt jwt) {
        Object claim = jwt.getClaim("userId");
        if (claim instanceof Number number) return number.intValue();
        if (claim instanceof String string) {
            try {
                return Integer.valueOf(string);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

}
