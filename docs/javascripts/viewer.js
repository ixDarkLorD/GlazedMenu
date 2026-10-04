// The home page's screenshot window (.gm-viewer): one frame, its slides side by side in a track that scrolls and snaps.
// Without this script the track is simply swiped or scrolled; with it the window gets arrows, a caption and dots.
(function () {
  function button(className, label, text) {
    var element = document.createElement("button");
    element.type = "button";
    element.className = className;
    element.setAttribute("aria-label", label);
    if (text) element.textContent = text;
    return element;
  }

  function setup(viewer) {
    if (viewer.hasAttribute("data-gm-ready")) return;
    var track = viewer.querySelector(".gm-viewer__track");
    var slides = Array.prototype.slice.call(track.querySelectorAll(".gm-viewer__slide"));
    if (slides.length < 2) return;
    viewer.setAttribute("data-gm-ready", "");
    var current = 0;

    var bar = document.createElement("div");
    bar.className = "gm-viewer__bar";
    var previous = button("gm-viewer__arrow", "Previous screenshot", "‹");
    var next = button("gm-viewer__arrow", "Next screenshot", "›");
    var caption = document.createElement("div");
    caption.className = "gm-viewer__caption";
    caption.setAttribute("aria-live", "polite");
    var dots = document.createElement("div");
    dots.className = "gm-viewer__dots";
    var dotButtons = slides.map(function (slide, index) {
      var text = slide.querySelector("figcaption");
      var dot = button("gm-viewer__dot", text ? text.textContent : "Screenshot " + (index + 1));
      dot.addEventListener("click", function () { show(index); });
      dots.appendChild(dot);
      return dot;
    });
    bar.appendChild(previous);
    bar.appendChild(caption);
    bar.appendChild(next);
    viewer.appendChild(bar);
    viewer.appendChild(dots);

    function mark(index) {
      current = index;
      var text = slides[index].querySelector("figcaption");
      caption.textContent = text ? text.textContent : "";
      dotButtons.forEach(function (dot, i) {
        dot.classList.toggle("gm-viewer__dot--active", i === index);
      });
    }

    // Slides to a screenshot. Should the smooth scroll not get there (a browser that doesn't animate it), the track
    // jumps instead; until then the track's own scroll events don't change the current slide.
    var moving;
    function show(index) {
      index = (index + slides.length) % slides.length;
      var left = slides[index].offsetLeft - slides[0].offsetLeft;
      track.scrollTo({ left: left, behavior: "smooth" });
      mark(index);
      clearTimeout(moving);
      moving = setTimeout(function () {
        moving = null;
        if (Math.abs(track.scrollLeft - left) > 2) track.scrollLeft = left;
      }, 700);
    }

    previous.addEventListener("click", function () { show(current - 1); });
    next.addEventListener("click", function () { show(current + 1); });
    track.addEventListener("keydown", function (event) {
      if (event.key === "ArrowLeft") { event.preventDefault(); show(current - 1); }
      if (event.key === "ArrowRight") { event.preventDefault(); show(current + 1); }
    });

    // Swiping or scrolling the track: once it settles, the slide it rests on is the current one.
    var settle;
    track.addEventListener("scroll", function () {
      clearTimeout(settle);
      settle = setTimeout(function () {
        if (moving) return;
        var index = Math.round(track.scrollLeft / track.clientWidth);
        if (index !== current && slides[index]) mark(index);
      }, 120);
    });

    mark(0);
  }

  function setupAll() {
    Array.prototype.forEach.call(document.querySelectorAll("[data-gm-viewer]"), setup);
  }

  // Instant navigation swaps the page without reloading scripts: set up again whenever a page is shown.
  if (typeof document$ !== "undefined") document$.subscribe(setupAll);
  else setupAll();
})();
