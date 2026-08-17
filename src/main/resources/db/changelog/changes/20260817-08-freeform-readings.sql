--liquibase formatted sql

--changeset arkana:20260817-12-add-spread-kind
alter table spreads add column kind varchar(16) default 'STRUCTURED' not null;
alter table spreads add constraint spreads_kind_check
  check (kind in ('STRUCTURED', 'FREEFORM'));

--changeset arkana:20260817-13-seed-freeform-spread
insert into spreads (
  id, display_order, name_pt_br, name_en,
  short_description_pt_br, short_description_en,
  description_pt_br, description_en,
  use_case_pt_br, use_case_en,
  position_count, active, kind
)
select
  'free', coalesce(max(display_order), 0) + 1,
  'Leitura livre', 'Free reading',
  'Adicione quantas cartas precisar e organize a mesa livremente.',
  'Add as many cards as needed and arrange the table freely.',
  'Uma mesa sem posições predefinidas, construída carta por carta durante a consulta.',
  'A table without predefined positions, built card by card during the consultation.',
  'Para leituras intuitivas e métodos criados no momento.',
  'For intuitive readings and methods created in the moment.',
  0, true, 'FREEFORM'
from spreads;

--changeset arkana:20260817-14-enable-freeform-reading-positions
alter table reading_positions alter column spread_position_id drop not null;
alter table reading_positions add column stack_order integer;
update reading_positions set stack_order = position_order;
alter table reading_positions alter column stack_order set not null;
alter table reading_positions add constraint reading_positions_x_check check (x between 0 and 100);
alter table reading_positions add constraint reading_positions_y_check check (y between 0 and 100);
alter table reading_positions add constraint reading_positions_stack_order_check check (stack_order > 0);
