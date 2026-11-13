package com.example.morning_news;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/**
 * ログインの設定。
 * 自分用のパスワード1つでログインし、30日間ログイン状態を保持する。
 */
@Configuration
public class SecurityConfig {

    /** ログイン用のユーザー名。ログイン画面では入力させず、画面側で自動で送る */
    public static final String OWNER_USER = "owner";

    /** ログインを保持する期間（30日間、秒で指定） */
    private static final int REMEMBER_ME_SECONDS = 60 * 60 * 24 * 30;

    /** ログインできるユーザー（自分1人だけ）。パスワードは環境変数から受け取る */
    @Bean
    public UserDetailsService userDetailsService(@Value("${app.password}") String password) {
        return new InMemoryUserDetailsManager(
                User.withUsername(OWNER_USER)
                        .password("{noop}" + password)
                        .roles("OWNER")
                        .build());
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            UserDetailsService userDetailsService,
            @Value("${app.remember-me-key}") String rememberMeKey) throws Exception {

        http
                // ログイン画面以外は、すべてログインが必要
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/login.html", "/favicon.ico").permitAll()
                        .anyRequest().authenticated())

                // 自作のログイン画面を使う
                .formLogin(form -> form
                        .loginPage("/login.html")
                        .loginProcessingUrl("/login")
                        .failureUrl("/login.html?error")
                        .permitAll())

                // ログイン状態を30日間ブラウザに覚えさせる
                .rememberMe(remember -> remember
                        .key(rememberMeKey)
                        .tokenValiditySeconds(REMEMBER_ME_SECONDS)
                        .alwaysRemember(true)
                        .userDetailsService(userDetailsService))

                // CSRF対策は無効にする（理由は説明を参照）
                .csrf(csrf -> csrf.disable());

        return http.build();
    }
}
