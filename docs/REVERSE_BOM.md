# BOM bootstrapper.exe — что внутри бинаря и где взять исходники каждой части

SHA256 `7a367d89…901af12`. Вывод: **полный исходник из exe не восстанавливается никем** —
декомпилятор отдаёт ассемблер/псевдокод без имён, типов, макросов и структуры проекта.
Но 95% бинаря — открытый код, его версии сняты точно, качай с crates.io и получишь
те же самые исходники библиотек. Закрыты только ~12 файлов самой Pekora (список ниже) —
по ним есть лишь поведенческая спека (см. REVERSE_NOTES.md).

## Свои файлы Pekora (закрыты, исходников нет)

| Файл | Что делает (по строкам/импортам/логу) |
|---|---|
| `src/voice/mod.rs` | корень войс-модуля |
| `src/voice/devices.rs` | перечисление микрофонов/динамиков (WASAPI через cpal) |
| `src/voice/capture.rs` | захват мика, конфиг `voice.json` |
| `src/voice/resample.rs` | ресемпл в 48 kHz |
| `src/voice/mixer.rs` | микшер входящих потоков (ringbuf) |
| `src/voice/net.rs` | UDP relay: hello/session/keepalive/reconnect |
| `src/rpc.rs` | `GET pekora.pro/v1/voice/launcher-token`, `PEKORA_VOICE_TOKEN/ADDR` |
| `src/launcher.rs` | скачивание/обновление лаунчера (`/setup/launcher.exe`, `version.txt`) |
| `src/installer.rs` | установка, file-assoc, URL-scheme, `bootstrapper.old` ротация |
| `src/ui.rs` | окно на eframe/egui (инсталлер, `PekoraVoicemic.png`) |
| `src/fps.rs`, `src/fpsmem.rs` | FPS-трей (`PekoraFpsTray`), пресеты 60/75/120/144/165/240/Unlimited |

## Сторонние крейты — открытые, версии точные (93 шт.)

Бери с crates.io/GitHub, версии пин в Cargo.toml — получишь тот же код, что в exe:

ab_glyph_rasterizer 0.1.10, ahash 0.8.12, aho-corasick 1.1.4, anstream 1.0.0,
anstyle 1.0.14, anstyle-parse 1.0.0, anstyle-wincon 3.0.11, anyhow 1.0.102,
atomic-waker 1.1.2, audiopus_sys 0.2.2, base64 0.22.1, byteorder 1.5.0,
bytes 1.11.1, cpal 0.15.3, crc32fast 1.5.0, discord-rich-presence 0.2.5,
easyfft 0.4.2, ecolor 0.27.2, eframe 0.27.2, egui 0.27.2, egui-winit 0.27.2,
egui_extras 0.27.2, egui_glow 0.27.2, env_filter 1.0.1, env_logger 0.11.10,
epaint 0.27.2, fdeflate 0.3.7, flate2 1.1.9, futures-channel 0.3.32,
futures-core 0.3.32, futures-util 0.3.32, glow 0.13.1, glutin 0.31.3,
hashbrown 0.17.0, http 1.4.0, http-body-util 0.1.3, httparse 1.10.1,
hyper 1.9.0, hyper-rustls 0.27.9, hyper-util 0.1.20, icu_normalizer 2.2.0,
idna 1.1.0, image 0.24.9, image 0.25.10, indexmap 2.14.0, ipnet 2.12.0,
iri-string 0.7.12, itoa 1.0.18, jiff 0.2.24, memchr 2.8.0, miniz_oxide 0.8.9,
mio 1.2.0, nnnoiseless 0.5.2, num-integer 0.1.47, once_cell 1.21.4,
opus 0.3.1, parking_lot 0.12.5, parking_lot_core 0.9.12,
percent-encoding 2.3.2, png 0.17.16, png 0.18.1, primal-check 0.3.4,
realfft 3.5.0, regex 1.12.3, regex-automata 0.4.14, regex-syntax 0.8.10,
reqwest 0.12.28, ring 0.17.14, ringbuf 0.4.8, rustfft 6.4.1, rustls 0.23.39,
rustls-pki-types 1.14.1, rustls-webpki 0.103.13, serde_json 1.0.149,
smallvec 1.15.1, smol_str 0.2.2, socket2 0.6.3, strength_reduce 0.2.4,
tokio 1.52.1, tokio-rustls 0.26.4, tower 0.5.3, transpose 0.2.3,
ttf-parser 0.25.1, unicode-segmentation 1.13.2, untrusted 0.9.0, url 2.5.8,
utf8_iter 1.0.4, uuid 0.8.2, want 0.3.1, webbrowser 0.8.15, windows 0.54.0,
winit 0.29.15, zip 2.4.2
(rustc b940084d7eb6a299eb4bfeb8e34901bc051e7ac4)

## Что реально можно получить вместо «сурса»

1. Этот BOM + REVERSE_NOTES.md (архитектура, эндпоинты, параметры Opus/UDP) — уже есть.
2. Наша чистая re-implementation войса (Android-клиент + voice-relay) — уже собрана и работает.
3. Пофункционный псевдокод из Ghidra по конкретным адресам (rpc `0x140009700`, net `0x140017500`) —
   если понадобится точный байтовый layout их протокола. Скажи — сниму.
