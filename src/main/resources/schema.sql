PRAGMA journal_mode = WAL;

create table if not exists post(
    id integer primary key autoincrement,
    titulo text not null,
    capa text not null,
    slug text unique not null,
    markdown text not null,
    publicado INTEGER NOT NULL DEFAULT 1,
    criado_em TEXT NOT NULL DEFAULT (datetime('now', 'localtime')),
    categoria_id INTEGER,
    FOREIGN KEY (categoria_id) REFERENCES categoria(id)
);

CREATE TABLE IF NOT EXISTS categoria (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    nome TEXT NOT NULL,
    slug TEXT UNIQUE NOT NULL
);

CREATE TABLE IF NOT EXISTS tag (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    nome TEXT NOT NULL,
    slug TEXT UNIQUE NOT NULL
);

CREATE TABLE IF NOT EXISTS post_tag (
    post_id INTEGER,
    tag_id INTEGER,
    PRIMARY KEY (post_id, tag_id),
    FOREIGN KEY (post_id) REFERENCES post(id) ON DELETE CASCADE,
    FOREIGN KEY (tag_id) REFERENCES tag(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_post_publicado_data ON post(criado_em DESC, id DESC) WHERE publicado = 1;
CREATE INDEX IF NOT EXISTS idx_post_categoria_data ON post(categoria_id, criado_em DESC, id DESC) WHERE publicado = 1;
CREATE INDEX IF NOT EXISTS idx_post_tag_tag_post ON post_tag(tag_id, post_id);
