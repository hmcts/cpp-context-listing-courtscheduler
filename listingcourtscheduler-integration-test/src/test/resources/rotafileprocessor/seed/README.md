# Court schedule seed fixtures for the rota ITs

Rota processing only assigns judiciary to court schedules that already exist; it never creates them.
The rota ITs (`NewRotaFileProcessorIT`, `CleanRedundantRotaDataIT`) therefore load these court schedules
with `DatabaseSeeder.seedCourtSchedulesFromCsv(...)` before posting a rota file.

| File | Rota file it matches | Rows |
|------|----------------------|------|
| `court_schedule_IT_Test_lja_bedfordshire_rota_20240402T180039Z.csv` | `../IT_Test_lja_bedfordshire_rota_20240402T180039Z.xml` | 623 |
| `court_schedule_IT_Test_lja_bedfordshire_rota_20240402T190039Z.csv` | `../IT_Test_lja_bedfordshire_rota_20240402T190039Z.xml` | 620 |
| `court_schedule_IT_Test_lja_bedfordshire_rotaa_20240401T180039Z.csv` | `../IT_Test_lja_bedfordshire_rotaa_20240401T180039Z.xml` | 586 |

## Where they came from

Captured once (SPRDT-971, base commit `5ea21d3` on `team/SPRDT967`) by running the legacy rota processor
(`rotaProcess: "old"`, since removed) on each rota file against the IT stack, with OU `B40IM00` marked
non-migrated and the reference-data stubs each test uses (default stubs for the `20240402` files, the
`-updated` stubs for `rotaa_20240401`), then dumping the table:

```sql
COPY (SELECT id, court_listing_profile_id, oucode, court_room_id, court_room_number, court_house_id,
             court_house_name, court_room_name, operational_unit, rota_business_type, panel, court_session,
             is_slot_based, session_start, max_slot, max_duration_mins, available_slot, available_duration_mins,
             active, support_ad_split, max_ad_morning_duration, max_ad_afternoon_duration, session_start_time,
             session_end_time, national_break_time, is_overbooking_allowed, is_draft, jurisdiction
      FROM court_schedule
      ORDER BY session_start, oucode, court_room_number, court_session, id)
TO STDOUT WITH CSV HEADER
```

The legacy processor no longer exists, so they can't be regenerated that way. To change them, edit the CSV
directly or dump rows created through the session APIs with the same query.

## Rules

- The header row is the column list passed to `COPY court_schedule (...)`, so it must name real
  `court_schedule` columns. If a Liquibase change renames or adds a NOT NULL column without a default,
  update the header and rows.
- `created_on` / `updated_on` are deliberately omitted so they take the column defaults (seed time).
- The data is court/session reference data only. Every `court_listing_profile_id` already appears in the
  matching rota XML. Don't add judiciary or any other personal data here.
