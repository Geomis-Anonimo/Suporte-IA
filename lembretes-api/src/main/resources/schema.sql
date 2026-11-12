CREATE TABLE IF NOT EXISTS lembrete (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  titulo TEXT NOT NULL,
  descricao TEXT,
  mensagem TEXT,
  rec_tipo TEXT NOT NULL,
  rec_data_hora TEXT,
  rec_hora TEXT,
  rec_dia_semana TEXT,
  rec_dia_mes INTEGER,
  antecedencia_min INTEGER NOT NULL,
  contexto_md TEXT,
  participantes_json TEXT NOT NULL DEFAULT '[]',
  proxima_ocorrencia INTEGER,
  proximo_disparo INTEGER,
  ativo INTEGER NOT NULL DEFAULT 1,
  criado_em INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_lembrete_disparo ON lembrete (ativo, proximo_disparo);

CREATE TABLE IF NOT EXISTS disparo (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  lembrete_id INTEGER NOT NULL,
  ocorrencia INTEGER NOT NULL,
  enviado_em INTEGER NOT NULL,
  status TEXT NOT NULL,
  detalhe TEXT,
  UNIQUE (lembrete_id, ocorrencia)
);
