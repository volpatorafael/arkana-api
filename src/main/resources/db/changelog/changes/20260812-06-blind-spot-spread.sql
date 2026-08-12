--liquibase formatted sql

--changeset arkana:20260812-06-seed-blind-spot-spread
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
  'blind-spot',
  11,
  'Ponto Cego',
  'Blind Spot',
  'Quatro perspectivas para revelar aspectos que escapam à percepção.',
  'Four perspectives that reveal aspects beyond conscious perception.',
  'Uma leitura de autoconhecimento que explora a identidade pessoal, o desconhecido, a sombra e aquilo que ainda não se consegue enxergar.',
  'A self-awareness reading that explores personal identity, the unknown, the shadow, and what cannot yet be seen.',
  'Ideal para reconhecer padrões ocultos e ampliar a consciência sobre si.',
  'Best for recognizing hidden patterns and expanding self-awareness.',
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
  ('blind-spot', '1', 1, 'Identidade pessoal', 'Personal identity', 'Identidade pessoal.', 'Personal identity.', 35, 30, 0),
  ('blind-spot', '2', 2, 'O grande desconhecido', 'The great unknown', 'O grande desconhecido.', 'The great unknown.', 65, 70, 0),
  ('blind-spot', '3', 3, 'A sombra', 'The shadow', 'A sombra.', 'The shadow.', 35, 70, 0),
  ('blind-spot', '4', 4, 'O ponto cego', 'The blind spot', 'O ponto cego.', 'The blind spot.', 65, 30, 0);
