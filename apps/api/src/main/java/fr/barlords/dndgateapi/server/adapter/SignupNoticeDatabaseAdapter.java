package fr.barlords.dndgateapi.server.adapter;

import fr.barlords.dndgateapi.domain.port.out.SignupNoticeLogSpi;
import fr.barlords.dndgateapi.server.repository.SignupNoticeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
public class SignupNoticeDatabaseAdapter implements SignupNoticeLogSpi {

    private final SignupNoticeRepository repository;

    @Override
    @Transactional
    public boolean claim(String email, Instant now, Instant windowStart) {
        return repository.claim(hash(email), now, windowStart) == 1;
    }

    @Override
    @Transactional
    public void deleteSentBefore(Instant before) {
        repository.deleteSentBefore(before);
    }

    private static String hash(String email) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(email.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available", e);
        }
    }

}
