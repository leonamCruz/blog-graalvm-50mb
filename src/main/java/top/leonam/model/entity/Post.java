package top.leonam.model.entity;

import java.time.LocalDateTime;

public class Post {
    public long id;
    public String titulo;
    public String capa;
    public String slug;
    public String markdown;
    public LocalDateTime criadoEm;
    public boolean publicado;
    public long categoriaId;
}
