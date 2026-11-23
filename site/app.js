(() => {
  'use strict';

  const REPO = 'l4yerzer0/undercover';
  const REPO_URL = `https://github.com/${REPO}`;
  const RELEASES_URL = `${REPO_URL}/releases/latest`;
  const API_LATEST = `https://api.github.com/repos/${REPO}/releases/latest`;

  const state = {
    version: 'v0.5',
    apkUrl: RELEASES_URL,
    apkName: 'Undercover APK',
    platform: null,
  };

  /* ---------------------------------------------------------
     1. Scroll Reveal Animations
     --------------------------------------------------------- */
  function initReveal() {
    const targets = document.querySelectorAll('.reveal, .reveal-rule');
    if (!('IntersectionObserver' in window)) {
      targets.forEach((el) => el.classList.add('is-visible'));
      return;
    }

    const observer = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          if (entry.isIntersecting) {
            entry.target.classList.add('is-visible');
            observer.unobserve(entry.target);
          }
        });
      },
      { threshold: 0.12, rootMargin: '0px 0px -24px 0px' }
    );

    targets.forEach((el) => observer.observe(el));
  }

  /* ---------------------------------------------------------
     2. Interactive "What to Choose" Card (App vs CLI)
     --------------------------------------------------------- */
  const CHOICES = {
    app: {
      title: 'Клиент с интерфейсом',
      badge: 'ANDROID ГОТОВ',
      badgeClass: 'badge--ready',
      desc: 'Для повседневного использования на телефоне и компьютере. Импорт профилей по ссылке или QR, выбор узлов и встроенный обход DPI.',
      rows: [
        { key: 'ПЛАТФОРМЫ', value: 'Android (готов) · Desktop (WIP)' },
        { key: 'УПРАВЛЕНИЕ', value: 'Интерфейс Dossier' },
        { key: 'СЦЕНАРИЙ', value: 'Подключение к готовым узлам' },
        { key: 'ОБНОВЛЕНИЯ', value: 'Проверка из GitHub Releases' },
      ],
      footNote: 'ДЛЯ ПОЛЬЗОВАТЕЛЕЙ',
      footLinkText: 'К ЗАГРУЗКЕ ↓',
      footLinkHref: '#download',
      footExternal: false,
    },
    cli: {
      title: 'Консольная утилита uc',
      badge: 'В РАЗРАБОТКЕ',
      badgeClass: 'badge--wip',
      desc: 'Для тех, кто поднимает свой VPN-сервер или работает из терминала. Развёртывание VLESS Reality на VPS по SSH, управление ключами и QR.',
      rows: [
        { key: 'ПЛАТФОРМЫ', value: 'Linux · macOS · Windows (Go)' },
        { key: 'ДЕПЛОЙ VPS', value: 'uc deploy — VLESS Reality по SSH' },
        { key: 'УПРАВЛЕНИЕ', value: 'Пользователи, экспорт QR, doctor' },
        { key: 'СЦЕНАРИЙ', value: 'Свои узлы и работа в терминале' },
      ],
      footNote: 'ДЛЯ АДМИНОВ И СВОИХ VPS',
      footLinkText: 'ИСХОДНИКИ CLI ↗',
      footLinkHref: `${REPO_URL}/tree/main/cli`,
      footExternal: true,
    },
  };

  function initChoiceCard() {
    const panel = document.getElementById('choice-panel');
    if (!panel) return;

    const body = document.getElementById('choice-body');
    const titleEl = document.getElementById('choice-title');
    const badgeEl = document.getElementById('choice-badge');
    const descEl = document.getElementById('choice-desc');
    const rowsEl = document.getElementById('choice-rows');
    const footNoteEl = document.getElementById('choice-foot-note');
    const footLinkEl = document.getElementById('choice-foot-link');
    const tabs = panel.querySelectorAll('.spec-tab');

    function renderChoice(id) {
      const item = CHOICES[id];
      if (!item) return;

      titleEl.textContent = item.title;
      badgeEl.textContent = item.badge;
      badgeEl.className = `badge ${item.badgeClass}`;
      descEl.textContent = item.desc;

      rowsEl.innerHTML = item.rows
        .map(
          (row) => `
            <div class="dossier-table__row" role="row">
              <span class="label-mono label-mono--muted" role="cell">${escapeHtml(row.key)}</span>
              <span class="mono-val" role="cell">${escapeHtml(row.value)}</span>
            </div>
          `
        )
        .join('');

      footNoteEl.textContent = item.footNote;
      footLinkEl.textContent = item.footLinkText;
      footLinkEl.href = item.footLinkHref;
      if (item.footExternal) {
        footLinkEl.target = '_blank';
        footLinkEl.rel = 'noopener noreferrer';
      } else {
        footLinkEl.removeAttribute('target');
        footLinkEl.removeAttribute('rel');
      }
    }

    tabs.forEach((tab) => {
      tab.addEventListener('click', () => {
        if (tab.classList.contains('is-active')) return;
        const choice = tab.dataset.choice;
        tabs.forEach((t) => {
          const active = t === tab;
          t.classList.toggle('is-active', active);
          t.setAttribute('aria-selected', String(active));
        });

        body.classList.add('is-switching');
        setTimeout(() => {
          renderChoice(choice);
          body.classList.remove('is-switching');
        }, 140);
      });
    });

    renderChoice('app');
  }

  /* ---------------------------------------------------------
     2b. Ambient Dossier Background Canvas
     Coordinate grid + drifting geometric fragments (decomposing U motif)
     --------------------------------------------------------- */
  function initBackgroundCanvas() {
    const canvas = document.getElementById('bg-canvas');
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    const GRID_STEP = 76;
    let width = 0;
    let height = 0;
    let dpr = 1;
    let fragments = [];
    let rafId = null;
    let scanY = 0;
    const pointer = { x: -9999, y: -9999, active: false };

    function createFragment(randomY) {
      const isMint = Math.random() < 0.16;
      return {
        x: Math.random() * width,
        y: randomY ? Math.random() * height : height + 10 + Math.random() * 40,
        size: 2 + Math.floor(Math.random() * 4),
        vx: 0.08 + Math.random() * 0.18,
        vy: -(0.14 + Math.random() * 0.28),
        baseAlpha: isMint ? 0.16 + Math.random() * 0.12 : 0.06 + Math.random() * 0.1,
        phase: Math.random() * Math.PI * 2,
        speed: 0.008 + Math.random() * 0.015,
        mint: isMint,
      };
    }

    function resize() {
      dpr = Math.min(window.devicePixelRatio || 1, 2);
      width = window.innerWidth;
      height = window.innerHeight;
      canvas.width = Math.floor(width * dpr);
      canvas.height = Math.floor(height * dpr);
      ctx.setTransform(dpr, 0, 0, dpr, 0, 0);

      const targetCount = Math.max(22, Math.min(54, Math.floor((width * height) / 28000)));
      fragments = Array.from({ length: targetCount }, () => createFragment(true));
    }

    function draw(time) {
      ctx.clearRect(0, 0, width, height);

      // 1. Subtle dossier coordinate crosshairs
      const offsetX = (width % GRID_STEP) * 0.5;
      const offsetY = (height % GRID_STEP) * 0.5;

      for (let x = offsetX; x < width; x += GRID_STEP) {
        for (let y = offsetY; y < height; y += GRID_STEP) {
          let alpha = 0.055;
          let size = 2.5;

          if (pointer.active) {
            const dx = x - pointer.x;
            const dy = y - pointer.y;
            const dist = Math.hypot(dx, dy);
            if (dist < 180) {
              const boost = 1 - dist / 180;
              alpha += boost * 0.18;
              size += boost * 1.5;
            }
          }

          ctx.strokeStyle = `rgba(237, 231, 217, ${alpha.toFixed(3)})`;
          ctx.lineWidth = 1;
          ctx.beginPath();
          ctx.moveTo(x - size, y);
          ctx.lineTo(x + size, y);
          ctx.moveTo(x, y - size);
          ctx.lineTo(x, y + size);
          ctx.stroke();
        }
      }

      // 2. Slow horizontal dossier scanline
      if (!reducedMotion) {
        scanY = (scanY + 0.35) % (height + 160);
        const lineY = scanY - 80;
        const grad = ctx.createLinearGradient(0, lineY - 40, 0, lineY + 40);
        grad.addColorStop(0, 'rgba(237, 231, 217, 0)');
        grad.addColorStop(0.5, 'rgba(237, 231, 217, 0.03)');
        grad.addColorStop(1, 'rgba(237, 231, 217, 0)');
        ctx.fillStyle = grad;
        ctx.fillRect(0, lineY - 40, width, 80);
      }

      // 3. Drifting geometric fragments (logo dissolving-U motif)
      for (let i = 0; i < fragments.length; i++) {
        const f = fragments[i];
        if (!reducedMotion) {
          f.x += f.vx;
          f.y += f.vy;
          f.phase += f.speed;
          if (f.y < -16 || f.x > width + 16) {
            fragments[i] = createFragment(false);
            if (Math.random() < 0.4) {
              fragments[i].x = -8;
              fragments[i].y = Math.random() * height;
            }
            continue;
          }
        }

        const pulse = 0.65 + 0.35 * Math.sin(f.phase + (time || 0) * 0.0004);
        const alpha = f.baseAlpha * pulse;
        ctx.fillStyle = f.mint
          ? `rgba(143, 227, 176, ${alpha.toFixed(3)})`
          : `rgba(237, 231, 217, ${alpha.toFixed(3)})`;
        ctx.fillRect(Math.round(f.x), Math.round(f.y), f.size, f.size);
      }

      if (!reducedMotion) {
        rafId = requestAnimationFrame(draw);
      }
    }

    window.addEventListener('resize', resize, { passive: true });
    window.addEventListener(
      'pointermove',
      (e) => {
        pointer.x = e.clientX;
        pointer.y = e.clientY;
        pointer.active = true;
      },
      { passive: true }
    );
    window.addEventListener('pointerleave', () => {
      pointer.active = false;
    });
    document.addEventListener('visibilitychange', () => {
      if (reducedMotion) return;
      if (document.hidden && rafId) {
        cancelAnimationFrame(rafId);
        rafId = null;
      } else if (!document.hidden && !rafId) {
        rafId = requestAnimationFrame(draw);
      }
    });

    resize();
    draw(0);
  }

  /* ---------------------------------------------------------
     3. Platform Detection & Smart Download Block
     --------------------------------------------------------- */
  function detectPlatform() {
    const ua = navigator.userAgent || '';
    const uad = navigator.userAgentData;
    const rawPlatform = (uad && uad.platform ? uad.platform : navigator.platform || '').toLowerCase();

    if (/android/i.test(ua) || rawPlatform.includes('android')) {
      return { id: 'android', name: 'Android', kind: 'mobile' };
    }
    if (/iphone|ipad|ipod/i.test(ua) || (rawPlatform === 'macintel' && navigator.maxTouchPoints > 1)) {
      return { id: 'ios', name: 'iOS / iPadOS', kind: 'mobile' };
    }
    if (rawPlatform.includes('win') || /windows/i.test(ua)) {
      return { id: 'windows', name: 'Windows', kind: 'desktop' };
    }
    if (rawPlatform.includes('mac') || /macintosh|mac os x/i.test(ua)) {
      return { id: 'macos', name: 'macOS', kind: 'desktop' };
    }
    if (rawPlatform.includes('linux') || /linux/i.test(ua)) {
      return { id: 'linux', name: 'Linux', kind: 'desktop' };
    }
    return { id: 'unknown', name: 'Не определена', kind: 'unknown' };
  }

  function renderFeaturedDownload() {
    const container = document.getElementById('download-featured');
    const platformLabel = document.getElementById('detected-platform-label');
    const heroBtnLabel = document.getElementById('hero-download-label');
    const detectDot = document.getElementById('detect-dot');
    if (!container) return;

    const p = state.platform;
    platformLabel.textContent = p.name.toUpperCase();

    if (p.id === 'android') {
      detectDot.classList.add('status-dot--active');
      if (heroBtnLabel) heroBtnLabel.textContent = 'СКАЧАТЬ ДЛЯ ANDROID';

      container.innerHTML = `
        <div>
          <div class="download-featured__eyebrow">
            <span class="label-mono label-mono--muted">РЕКОМЕНДУЕМАЯ СБОРКА</span>
            <span class="badge badge--ready">ДОСТУПНО · ${escapeHtml(state.version)}</span>
          </div>
          <h3 class="download-featured__title">Undercover для Android</h3>
          <p class="download-featured__desc">
            Полный клиент с поддержкой sing-box, Xray, VLESS Reality, Hysteria2 и встроенного ByeDPI. Устанавливается напрямую из APK без Google Play.
          </p>
          <div class="download-featured__note download-featured__note--ready">
            Требуется Android 7.0+ (API 24). Сборка подписана и опубликована через GitHub Actions.
          </div>
        </div>
        <div class="download-featured__cta">
          <a href="${escapeAttr(state.apkUrl)}" class="btn btn--primary btn--full" target="_blank" rel="noopener noreferrer">
            СКАЧАТЬ APK (${escapeHtml(state.version)})
          </a>
          <a href="${RELEASES_URL}" class="btn btn--outlined btn--full" target="_blank" rel="noopener noreferrer">
            ВСЕ ФАЙЛЫ РЕЛИЗА ↗
          </a>
          <span class="download-featured__meta">${escapeHtml(state.apkName)} · GitHub Releases</span>
        </div>
      `;
      return;
    }

    if (p.kind === 'desktop') {
      detectDot.classList.remove('status-dot--active');
      detectDot.style.background = 'var(--amber)';
      if (heroBtnLabel) heroBtnLabel.textContent = `СКАЧАТЬ (${p.name.toUpperCase()})`;

      container.innerHTML = `
        <div>
          <div class="download-featured__eyebrow">
            <span class="label-mono label-mono--muted">ДЛЯ ВАШЕЙ ОС · ${escapeHtml(p.name.toUpperCase())}</span>
            <span class="badge badge--wip">В РАЗРАБОТКЕ</span>
          </div>
          <h3 class="download-featured__title">Desktop-клиент и CLI <code class="inline-code">uc</code> для ${escapeHtml(p.name)}</h3>
          <p class="download-featured__desc">
            Десктопное приложение на Compose Desktop и консольная утилита <code class="inline-code">uc</code> на Go для ${escapeHtml(p.name)} сейчас в активной разработке. Исходный код уже открыт в репозитории.
          </p>
          <div class="download-featured__note">
            Стабильный релиз сейчас доступен для Android (${escapeHtml(state.version)}). Сборки Desktop и CLI появятся в следующих релизах.
          </div>
        </div>
        <div class="download-featured__cta">
          <a href="${REPO_URL}/tree/main/cli" class="btn btn--outlined btn--full" target="_blank" rel="noopener noreferrer">
            CLI (UC) НА GITHUB ↗
          </a>
          <a href="${REPO_URL}/tree/main/desktop" class="btn btn--outlined btn--full" target="_blank" rel="noopener noreferrer">
            DESKTOP НА GITHUB ↗
          </a>
          <a href="${escapeAttr(state.apkUrl)}" class="btn btn--primary btn--full" target="_blank" rel="noopener noreferrer">
            СКАЧАТЬ ANDROID APK (${escapeHtml(state.version)})
          </a>
        </div>
      `;
      return;
    }

    // iOS or Unknown
    detectDot.classList.remove('status-dot--active');
    if (heroBtnLabel) heroBtnLabel.textContent = 'СМОТРЕТЬ СБОРКИ';

    const title = p.id === 'ios'
      ? 'Клиент для iOS пока не планируется'
      : 'Выберите подходящую сборку';
    const desc = p.id === 'ios'
      ? 'На данный момент готов клиент для Android, а Desktop-версия и CLI-утилита uc находятся в активной разработке.'
      : 'Не удалось автоматически определить ОС. Стабильный релиз доступен для Android, а Desktop и CLI сейчас в разработке.';

    container.innerHTML = `
      <div>
        <div class="download-featured__eyebrow">
          <span class="label-mono label-mono--muted">ДОСТУПНЫЕ СБОРКИ</span>
          <span class="badge badge--ready">ANDROID ${escapeHtml(state.version)}</span>
        </div>
        <h3 class="download-featured__title">${title}</h3>
        <p class="download-featured__desc">${desc}</p>
      </div>
      <div class="download-featured__cta">
        <a href="${escapeAttr(state.apkUrl)}" class="btn btn--primary btn--full" target="_blank" rel="noopener noreferrer">
          СКАЧАТЬ ANDROID APK (${escapeHtml(state.version)})
        </a>
        <a href="${RELEASES_URL}" class="btn btn--outlined btn--full" target="_blank" rel="noopener noreferrer">
          ВСЕ РЕЛИЗЫ НА GITHUB ↗
        </a>
      </div>
    `;
  }

  /* ---------------------------------------------------------
     4. Platform Filter Tabs
     --------------------------------------------------------- */
  function initTabs() {
    const tabs = document.querySelectorAll('.tab');
    const rows = document.querySelectorAll('.target-row');

    tabs.forEach((tab) => {
      tab.addEventListener('click', () => {
        const filter = tab.dataset.filter;
        tabs.forEach((t) => {
          const active = t === tab;
          t.classList.toggle('is-active', active);
          t.setAttribute('aria-selected', String(active));
        });

        rows.forEach((row) => {
          row.hidden = filter !== 'all' && row.dataset.target !== filter;
        });
      });
    });
  }

  /* ---------------------------------------------------------
     5. Fetch Latest Release Metadata from GitHub API
     --------------------------------------------------------- */
  async function fetchLatestRelease() {
    try {
      const res = await fetch(API_LATEST, {
        headers: { Accept: 'application/vnd.github+json' },
      });
      if (!res.ok) return;
      const data = await res.json();

      if (data.tag_name) {
        state.version = data.tag_name;
        const heroVer = document.getElementById('hero-version');
        if (heroVer) heroVer.textContent = data.tag_name;
        document.querySelectorAll('[data-release-version]').forEach((el) => {
          el.textContent = data.tag_name;
        });
      }

      if (Array.isArray(data.assets)) {
        const apk =
          data.assets.find((a) => /arm64.*\.apk$/i.test(a.name)) ||
          data.assets.find((a) => /universal.*\.apk$/i.test(a.name)) ||
          data.assets.find((a) => /\.apk$/i.test(a.name));

        if (apk && apk.browser_download_url) {
          state.apkUrl = apk.browser_download_url;
          state.apkName = apk.name;
          document.querySelectorAll('[data-apk-link]').forEach((el) => {
            el.href = apk.browser_download_url;
          });
        }
      }

      renderFeaturedDownload();
    } catch {
      // Fallback to static v0.5 / releases/latest silently
    }
  }

  function escapeHtml(str) {
    return String(str)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;');
  }

  function escapeAttr(str) {
    return escapeHtml(str).replace(/"/g, '&quot;');
  }

  /* ---------------------------------------------------------
     Init
     --------------------------------------------------------- */
  state.platform = detectPlatform();
  initReveal();
  initBackgroundCanvas();
  initChoiceCard();
  renderFeaturedDownload();
  initTabs();
  fetchLatestRelease();
})();
