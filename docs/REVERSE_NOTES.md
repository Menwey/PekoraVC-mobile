# REVERSE NOTES — Pekora bootstrapper.exe voice

Дата: 2026-10-14. Инструмент: REA (`rea-agents@6.5.0`, без Hopper/Ghidra/IDA — только `inspect-artifact`, `inspect-rust-binary`, `inspect-pe-resources` + `strings`/`pefile`/`capstone`/`objdump` вручную). REA — это MCP для реверса: агент дергает инструменты статики/динамики (натив, JS/Electron, .NET, APK, pcap), получает findings с evidence и пишет реконструкцию. Без движка нативного анализа (`doctor`: hopper/ghidra/ida missing) глубокий декомпил недоступен — добить через Ghidra smoking gun-адреса ниже.

## Артефакт

- Путь: `E:\zzzz\there\bootstrapper.exe`, 6 962 688 байт
- SHA256: `7a367d894e0ab82fa4d4672a4a1b28fda9e815ac62fa88da411b8e42e901af12`
- PE32+ x86_64, `.text` 3 917 312, ImageBase `0x140000000`, rustc commit `b940084d7eb6a299eb4bfeb8e34901bc051e7ac4`
- Импорты: `ws2_32` (UDP: send/recv/getaddrinfo), `cpal` WASAPI, `opengl32`+`winit`+`eframe/egui-0.27.2` (UI), `shell32`/`advapi32` (URL-scheme + file assoc), `ole32` (COM audio)
- Ресурсы: иконки + PNG `PekoraVoicemic.png`, `mic_muted.png`, `mic_fill.png`, `check.png`, `pekora_logo`

## Voice-модули (строки, fileoff → va)

- `src/voice/mod.rs` @ `003cb552`
- `src/voice/devices.rs` @ `003cc718` — `Pekora_bootstrapper::voice::devices`, `voice: input stream:`, `voice: output stream:`, `no input device`, `Microphone unavailable`, `Speakers unavailable`
- `src/voice/net.rs` @ `003cc878` — `struct PosMsg with 4 elements`, `struct Prefs with 2 elements`, `voice: cannot resolve relay`, `Pekora_bootstrapper::voice::net`, `0.0.0.0:0` (fallback), `voice: connected (user `, `no-session`, `voice: relay closed session:`, `voice: relay went quiet, reconnecting`, `voice: udp setup failed:`
- `src/voice/resample.rs` @ `003cce38` (`Default`)
- `src/voice/capture.rs` @ `003ccf90` (`voice.json`) — `Pekora_bootstrapper::voice::capture`, `voice: mic '<name>'`, ` Hz x`, `unsupported input format`, `voice: input '<name>' failed:`
- `src/voice/mixer.rs` @ `003cd348` (`ringbuf-0.4.8`)
- `src/rpc.rs` @ `003f8e60` — `PEKORA_VOICE_TOKEN`, `PEKORA_VOICE_ADDR`, `http://pekora.pro`, `/v1/voice/launcher-token`, `User-Agent`/`PekoraLauncher`/`ticket`, `voice token unavailable:`
- `struct TokenResponse` + `struct StateMsg` @ `003fb0d8` (конкатенация serde-имен; `TokenResponse` = 3 элемента)
- `voice: opus encoder init failed` @ `003fae80`, `opus_custom_mode_create(48000, 960)` (assert в `celt_decoder.c`) = 48 kHz, 960 семплов = 20 мс
- Треды: `voice-net`, `voice-capture`, `voice-mixer`, `voice-devices`

## Сеть (наблюдено вживую 2026-10-14)

- `bootstrapper.exe --help` (с `PEKORA_NO_SELFUPDATE=1`): `GET http://pekora.pro/version` → `530 error code: 1033` (Cloudflare tunnel origin down); `GET /setup/2021/version.txt` аналогично; локальный кэш `2021M-4`, пропуск скачивания.
- `curl /v1/voice/launcher-token` (с `User-Agent: PekoraLauncher`) → тот же `530/1033`. Origin лежит — handshake снять нечем.
- Вывод: протокол восстанавливается только статикой + будущим захватом. Env-оверрайды `PEKORA_VOICE_TOKEN`/`PEKORA_VOICE_ADDR` — точка входа для своего relay (клиент умеет ходить мимо HTTP).

## Адреса для добивки в Ghidra/Hopper/IDA

- `0x140009700` ± `0x500` — `rpc.rs` fetch token (`User-Agent` xref `0x14000971a`, `PekoraLauncher` `0x1400097a4`, `ticket` `0x1400098ee`, `application/json` `0x140009a3d`). Рядом строится URL из `http://pekora.pro` + `/v1/voice/launcher-token` (конкатенация, поэтому прямого xref на полный URL нет).
- `0x14000b0fa`, `0x140030c23`, `0x140030cba` — чтения `PEKORA_VOICE_TOKEN`; `0x14000b11f`, `0x140030c38`, `0x140030cdc` — `PEKORA_VOICE_ADDR` (env fallback).
- `0x140017500` ± `0x400` — `voice::net` UDP setup (`0.0.0.0:0` xref `0x1400175f0`, `htons` через `rol ax,8`, `no-session` xref `0x140018450`, `relay went quiet` xref `0x140017be0`).
- `0x14001d5fe` — xref `struct StateMsg` (сериализация).
- Функции Opus: `opus_encoder.c` / `opus_decoder.c` asserts выше — искать `audiopus::Encoder::try_new(48000, …)` для точных битрейта/каналов.

## Что неизвестно (помечено UNKNOWN в коде)

1. Точные JSON-поля `TokenResponse` (3 шт.) — нужен один `mitmdump` ответа `/v1/voice/launcher-token`.
2. Формат UDP-пакетов: размер session-header, где токен, где sequence, где Opus-пейлоад; структуры `StateMsg`/`PosMsg(4)`/`Prefs(2)` — нужен один Wireshark-дамп relay.
3. Откуда берется `ticket` (аргумент `--play-url`? Roblox auth ticket?) — искать xref `0x1400098ee` в Ghidra.
4. Частота keepalive и условие `relay went quiet` (таймаут тишины, сек).
5. Входной/выходной сэмплрейт до ресемпла (` Hz x` логи) — снять на живом запуске с разными девайсами.
