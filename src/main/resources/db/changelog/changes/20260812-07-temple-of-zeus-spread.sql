--liquibase formatted sql

--changeset arkana:20260812-07-seed-temple-of-zeus-spread
insert into public.spreads (
  id,
  display_order,
  name_pt_br,
  name_en,
  short_description_pt_br,
  short_description_en,
  description_pt_br,
  description_en,
  use_case_pt_br,
  use_case_en,
  position_count,
  active
)
values (
  'temple-of-zeus',
  12,
  'Templo de Zeus',
  'Temple of Zeus',
  'Uma visão temporal da vida financeira com orientação prática.',
  'A temporal view of financial life with practical guidance.',
  'Uma leitura de quatro cartas que conecta o passado recente, a situação atual e o futuro próximo, finalizando com um conselho financeiro.',
  'A four-card reading that connects the recent past, current situation, and near future, ending with financial advice.',
  'Ideal para compreender a evolução de questões financeiras e orientar os próximos passos.',
  'Best for understanding the development of financial matters and guiding the next steps.',
  4,
  true
);

insert into public.spread_positions (
  spread_id,
  position_key,
  position_order,
  name_pt_br,
  name_en,
  meaning_pt_br,
  meaning_en,
  x,
  y,
  rotation
)
values
  ('temple-of-zeus', '1', 1, 'Passado recente', 'Recent past', 'Passado recente.', 'Recent past.', 22, 50, 0),
  ('temple-of-zeus', '2', 2, 'Situação atual', 'Current situation', 'Situação atual.', 'Current situation.', 50, 78, 0),
  ('temple-of-zeus', '3', 3, 'Futuro próximo', 'Near future', 'Futuro próximo.', 'Near future.', 78, 50, 0),
  ('temple-of-zeus', '4', 4, 'Conselho financeiro', 'Financial advice', 'Conselho financeiro.', 'Financial advice.', 50, 22, 0);
