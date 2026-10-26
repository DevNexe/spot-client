(function () {
  if (window.__nxMobile) return;
  window.__nxMobile = true;

  function go(path) {
    var a = document.querySelector('a[href="' + path + '"]');
    if (a) a.click(); else location.href = path;
  }

  function setOn(btn) {
    var bs = document.querySelectorAll('#nx-nav button');
    for (var i = 0; i < bs.length; i++) bs[i].classList.remove('on');
    if (btn) btn.classList.add('on');
  }

  function build() {
    if (!document.body || document.getElementById('nx-nav')) return;
    var nav = document.createElement('div');
    nav.id = 'nx-nav';
    var items = [
      ['⌂', 'Главная', function () { document.documentElement.classList.remove('nx-lib'); go('/'); }],
      ['⌕', 'Поиск', function () { document.documentElement.classList.remove('nx-lib'); go('/search'); }],
      ['▤', 'Медиатека', function () { document.documentElement.classList.toggle('nx-lib'); }],
      ['♥', 'Любимое', function () { document.documentElement.classList.remove('nx-lib'); go('/collection/tracks'); }]
    ];
    items.forEach(function (it) {
      var b = document.createElement('button');
      b.innerHTML = '<span>' + it[0] + '</span>' + it[1];
      b.addEventListener('click', function () { setOn(b); it[2](); });
      nav.appendChild(b);
    });
    document.body.appendChild(nav);
  }

  // закрываем оверлей медиатеки при выборе элемента в нём
  document.addEventListener('click', function (e) {
    if (!document.documentElement.classList.contains('nx-lib')) return;
    var bar = document.querySelector('.Root__nav-bar');
    if (bar && bar.contains(e.target) && e.target.closest('a')) {
      setTimeout(function () { document.documentElement.classList.remove('nx-lib'); }, 150);
    }
  }, true);

  // SPA может перерисовать страницу — держим панель на месте
  build();
  setInterval(build, 1000);
})();
