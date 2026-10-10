package top.leonam.model.repository;

import top.leonam.model.entity.Tag;

import java.util.List;

public interface TagRepository {

    Tag buscarPorSlug(String slug);

    List<Tag> buscarTodas();
}
