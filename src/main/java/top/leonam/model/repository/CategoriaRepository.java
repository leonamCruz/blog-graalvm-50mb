package top.leonam.model.repository;

import top.leonam.model.entity.Categoria;

import java.util.List;

public interface CategoriaRepository {

    Categoria buscarPorSlug(String slug);

    List<Categoria> buscarTodas();
}
