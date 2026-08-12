--liquibase formatted sql

--changeset arkana:20260812-05-seed-decision-spread
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
  'decision',
  10,
  'Decisão',
  'Decision',
  'Compare duas opções e compreenda seus possíveis resultados.',
  'Compare two options and understand their possible outcomes.',
  'Uma leitura de seis cartas que apresenta a energia geral da questão, o que considerar e o possível resultado de cada opção, finalizando com um conselho.',
  'A six-card reading that presents the overall energy of the issue, what to consider and the possible outcome of each option, ending with advice.',
  'Ideal para escolhas entre dois caminhos, propostas ou possibilidades.',
  'Best for choosing between two paths, proposals, or possibilities.',
  6,
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
  ('decision', '1', 1, 'Energia geral', 'Overall energy', 'Energia geral que envolve a questão.', 'The overall energy surrounding the issue.', 15, 50, 0),
  ('decision', '2', 2, 'Opção A: o que considerar', 'Option A: what to consider', 'Algo a considerar sobre a opção A no presente.', 'Something to consider about option A in the present.', 40, 25, 0),
  ('decision', '3', 3, 'Opção A: possível resultado', 'Option A: possible outcome', 'Possível resultado para a opção A.', 'A possible outcome for option A.', 60, 25, 0),
  ('decision', '4', 4, 'Opção B: o que considerar', 'Option B: what to consider', 'Algo a considerar sobre a opção B no presente.', 'Something to consider about option B in the present.', 40, 75, 0),
  ('decision', '5', 5, 'Opção B: possível resultado', 'Option B: possible outcome', 'Possível resultado para a opção B.', 'A possible outcome for option B.', 60, 75, 0),
  ('decision', '6', 6, 'Conselho', 'Advice', 'Conselho.', 'Advice.', 85, 50, 0);
