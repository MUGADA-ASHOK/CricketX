package com.ipl.auth.security;

import com.ipl.auth.entity.User;
import com.ipl.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    /**
     * Email-based authentication: the "username" Spring Security passes in
     * here is always the user's email address.
     */
    @Override
    public UserDetails loadUserByUsername(String identifier) {
        User user = userRepository.findByEmail(identifier)
                .or(() -> userRepository.findByUsername(identifier))
                .orElseThrow(() -> new UsernameNotFoundException("No user found for: " + identifier));
        return new UserPrincipal(user);
    }
}
