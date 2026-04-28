package realworld_backend.article.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import realworld_backend.article.model.Author;
import realworld_backend.article.repository.AuthorRepository;
import realworld_backend.auth.api.request.CurrentAuthUser;

import java.util.HashSet;
import java.util.Set;

@Service
@RequiredArgsConstructor

public class AuthorService {
    private final AuthorRepository authorRepository;

    public Author findByUsername(String authorName) {
       return authorRepository.findByUsername(authorName).orElse(null);
    }

}
