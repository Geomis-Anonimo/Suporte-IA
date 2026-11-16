CREATE TABLE IF NOT EXISTS squad (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  slug TEXT NOT NULL UNIQUE,
  nome TEXT NOT NULL,
  ordem INTEGER NOT NULL,
  arquivado INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS projeto (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  squad_id INTEGER NOT NULL REFERENCES squad (id),
  slug TEXT NOT NULL,
  nome TEXT NOT NULL,
  descricao TEXT,
  contexto_md TEXT,
  arquivado INTEGER NOT NULL DEFAULT 0,
  UNIQUE (squad_id, slug)
);

CREATE TABLE IF NOT EXISTS tarefa (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  squad_id INTEGER NOT NULL REFERENCES squad (id),
  projeto_id INTEGER REFERENCES projeto (id),
  titulo TEXT NOT NULL,
  descricao TEXT,
  status TEXT NOT NULL,
  motivo_bloqueio TEXT,
  prioridade TEXT NOT NULL,
  tags_json TEXT NOT NULL DEFAULT '[]',
  link TEXT,
  ordem REAL NOT NULL,
  criada_em INTEGER NOT NULL,
  atualizada_em INTEGER NOT NULL,
  concluida_em INTEGER
);

CREATE INDEX IF NOT EXISTS idx_tarefa_coluna ON tarefa (squad_id, status, ordem);
CREATE INDEX IF NOT EXISTS idx_tarefa_projeto ON tarefa (projeto_id);
