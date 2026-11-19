# Optional Supabase cloud sync

The app is fully usable offline without any account. Sync is opt-in and talks to **your own**
Supabase project (Postgres + Auth). The client uses the plain REST/Auth HTTP APIs; no service key is
ever placed in the app.

## 1. Create the project
1. Create a project at https://supabase.com.
2. Authentication → Providers → enable **Email**. (With "Confirm email" on, users confirm before first sign-in.)
3. Settings → API: copy the **Project URL** and **anon public key**.

## 2. Run this SQL (SQL editor)

```sql
-- One generic table: each row is one app record (diary entry, recipe, water entry, ...).
create table if not exists public.sync_records (
  user_id     uuid        not null default auth.uid() references auth.users(id) on delete cascade,
  entity_type text        not null,
  entity_id   text        not null,
  payload     jsonb,
  deleted     boolean     not null default false,
  version     bigint      not null default 1,
  updated_at  timestamptz not null default clock_timestamp(),
  primary key (user_id, entity_type, entity_id)
);
create index if not exists sync_records_user_updated on public.sync_records (user_id, updated_at);

alter table public.sync_records enable row level security;
create policy "read own records" on public.sync_records for select using (auth.uid() = user_id);
-- Writes only happen through push_record (security definer) below; no direct insert/update/delete policy.

-- Optimistic-concurrency write: refuses stale writes instead of overwriting (no blind overwrite of diary data).
create or replace function public.push_record(
  p_entity_type text, p_entity_id text, p_payload jsonb, p_deleted boolean, p_base_version bigint)
returns table(status text, version bigint)
language plpgsql security definer set search_path = public as $$
declare cur bigint;
begin
  if auth.uid() is null then raise exception 'not authenticated'; end if;
  select r.version into cur from sync_records r
   where r.user_id = auth.uid() and r.entity_type = p_entity_type and r.entity_id = p_entity_id for update;
  if cur is not null and (p_base_version is null or cur <> p_base_version) then
    return query select 'conflict'::text, cur; return;
  end if;
  insert into sync_records(user_id, entity_type, entity_id, payload, deleted, version, updated_at)
  values (auth.uid(), p_entity_type, p_entity_id, p_payload, p_deleted, coalesce(cur, 0) + 1, clock_timestamp())
  on conflict (user_id, entity_type, entity_id) do update
    set payload = excluded.payload, deleted = excluded.deleted, version = excluded.version, updated_at = excluded.updated_at;
  return query select 'ok'::text, coalesce(cur, 0) + 1;
end $$;
revoke all on function public.push_record from public;
grant execute on function public.push_record to authenticated;

-- Account deletion (removes the auth user; records cascade).
create or replace function public.delete_my_account()
returns void language plpgsql security definer set search_path = public, auth as $$
begin
  if auth.uid() is null then raise exception 'not authenticated'; end if;
  delete from auth.users where id = auth.uid();
end $$;
revoke all on function public.delete_my_account from public;
grant execute on function public.delete_my_account to authenticated;
```

## 3. Connect the app
Profile → **Account & cloud sync** → enter Project URL and anon key → Save → Create account / Sign in.
Everything already on the device is queued for the first upload.

## How sync works
- **Local first**: every change is committed to Room immediately; the UI never waits for the network.
- **Outbox**: changes are recorded in `sync_queue` (one coalesced row per record) inside the same transaction.
- **Pull** then **push**: pull fetches rows newer than the last cursor; push sends queued records with the
  last known server `version` (`p_base_version`). The server increments `version` on each accepted write.
- **Conflicts**: if the same record changed on another device (pull finds a remote change while a local one
  is pending, or the server refuses a stale push), both versions are stored in `sync_conflicts` and nothing
  is overwritten. Profile → Account & sync lists them with *Keep this device's* / *Use cloud version*.
  Records whose content is identical on both sides are not treated as conflicts.
- **Idempotent**: record ids are UUIDs; re-sending a record is harmless.
- **Background**: WorkManager runs sync every 6 h on network with exponential backoff; *Sync now* and
  *Retry failed* are manual triggers.
- **Isolation**: RLS restricts reads to the signed-in user; writes go through `push_record`, which uses `auth.uid()`.

Not implemented: password reset UI, OAuth providers, end-to-end encryption, syncing recipe photos
(image files stay on the device) and preferences/reminder settings (device-specific by design).
