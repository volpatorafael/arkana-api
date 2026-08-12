--liquibase formatted sql

--changeset arkana:20260812-04-seed-diamond-spread
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
  'diamond',
  9,
  'Diamante',
  'Diamond',
  'Cinco cartas para esclarecer e solucionar uma questão.',
  'Five cards to clarify and resolve an issue.',
  'Uma leitura em formato de diamante que revela a questão, as influências internas e externas, a ação necessária e a solução.',
  'A diamond-shaped reading that reveals the issue, inner and outer influences, the action required, and the solution.',
  'Ideal para situações que pedem clareza e um caminho prático para a solução.',
  'Best for situations that call for clarity and a practical path toward resolution.',
  5,
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
  ('diamond', '1', 1, 'A questão', 'The issue', 'A questão ou assunto que precisa de mais clareza.', 'The issue or subject that needs greater clarity.', 50, 50, 0),
  ('diamond', '2', 2, 'Influência interna', 'Inner influence', 'A influência daquilo que há em você e que não consegue enxergar.', 'The influence of what lies within you and that you cannot see.', 25, 50, 0),
  ('diamond', '3', 3, 'Influência externa', 'Outer influence', 'A influência daquilo que está ao seu redor e que já conseguiu enxergar.', 'The influence of what surrounds you and that you can already see.', 75, 50, 0),
  ('diamond', '4', 4, 'O que fazer', 'What to do', 'O que precisa fazer para conseguir solucionar o problema pelo qual está passando.', 'What you need to do to resolve the problem you are facing.', 50, 82, 0),
  ('diamond', '5', 5, 'A solução', 'The solution', 'A solução do problema.', 'The solution to the problem.', 50, 18, 0);
