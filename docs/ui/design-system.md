# ASP design system

The Angular frontend uses **Bootstrap 5.3** (Sass, no Bootstrap JavaScript), themed with the ASP brand, plus a few app-specific pieces Bootstrap has no component for.

| What | Where |
|---|---|
| Entry point: which Bootstrap parts are imported | `frontend/src/styles.scss` |
| Brand theme (Bootstrap Sass variables, light and dark) | `frontend/src/styles/_theme.scss` |
| Bootstrap utilities that are generated (allow-list) | `frontend/src/styles/_utilities.scss` |
| App tokens (`--asp-*`) | `frontend/src/styles/_tokens.scss` |
| Global refinements (focus, icons, buttons, touch targets) | `frontend/src/styles/_base.scss` |
| App shell, auth pages | `frontend/src/styles/_shell.scss`, `frontend/src/app/shared/shell.ts` |
| Native `<dialog>` as Bootstrap modal | `frontend/src/styles/_dialog.scss` |
| Tables that stack into cards on phones | `frontend/src/styles/_table-stack.scss` |
| Work-order status colours | `frontend/src/styles/_status.scss` |
| Number plate, plate input | `frontend/src/app/shared/plate.ts` (`<app-plate>`), `registration-fields.ts` (`<app-registration-fields>`), `plate-format.ts`; styles in `frontend/src/styles/_plate.scss` |
| Kanban board | `frontend/src/styles/_board.scss` |
| Icons | `frontend/src/app/shared/icon.ts` (`<app-icon>`) |
| Alert | `frontend/src/app/shared/alert.ts` (`<app-alert>`) |
| Theme | `frontend/src/app/shared/theme.service.ts`, `theme-toggle.ts` |

**Direction:** graphite app chrome with a workshop-amber accent. Archivo (variable font with a width axis) is used for all text, and headings use a semi-condensed width. Number plates are drawn as EU-style plates and are the one bold element. Keep everything else quiet.

**Rules for new screens**

- Build with Bootstrap components and utilities first; use the app classes below only for what Bootstrap lacks. Don't write component styles for things a utility does.
- Use Bootstrap's mode-aware colours: `text-body`, `text-body-secondary`, `bg-body-tertiary`, `text-{color}-emphasis`, `bg-{color}-subtle`, `border-{color}-subtle`. **Don't** use `text-primary`, `text-success`, `link-*`, `text-bg-*`, `bg-{color}` or `btn-outline-{color}` (other than `-secondary`) for text: they do not follow dark mode, and amber text fails contrast on light surfaces.
- Every visible string goes through `| t`, with keys in all three `messages.*.ts` files.
- Show ProblemDetail errors with `<app-alert>{{ error() }}</app-alert>` (`i18n.error(e)` already translates them).
- Check the screen at 360 px wide: the page must never scroll horizontally.
- Need a utility that is not generated yet? Add its key to `_utilities.scss` (see [Bundle size](#bundle-size)).
- Bootstrap's JavaScript, jQuery, Popper, ng-bootstrap and other UI libraries are not used. Angular signals drive the drawer, dialogs and toasts.

---

## Theme

### Brand mapping (`_theme.scss`)

| Bootstrap variable | Value | Use |
|---|---|---|
| `$primary` | `#f2b200` amber | `btn-primary`, checked checkboxes and range thumbs, active marks. Text on it is `#1b1400`. |
| `$link-color` / `$link-color-dark` | `#7a5100` / `#ffc933` | Links and `btn-link` (AA on surfaces in both modes) |
| `$secondary` | `#535b66` | Neutral tone (`bg-secondary-subtle` badges) |
| `$success`, `$info`, `$warning`, `$danger` | `#1a7f37`, `#1d5fbf`, `#f2b84b`, `#b42318` | Semantic tones; their `*-text-emphasis`, `*-bg-subtle` and `*-border-subtle` pairs are set explicitly for both modes |
| `$body-bg` / `-dark` | `#f3f4f6` / `#17191c` | Page |
| `$body-color` / `-dark` | `#16191d` / `#eceef1` | Text |
| `$body-secondary-color` / `-dark` | `#535b66` / `#a3abb6` | Muted text (`text-body-secondary`, `.form-text`, table heads) |
| `$body-secondary-bg` / `-dark` | `#e9ecef` / `#1b1d21` | Sunken wells: board columns, progress track |
| `$body-tertiary-bg` / `-dark` | `#f7f8fa` / `#25292e` | Table head, modal footer, totals |
| `$border-color` / `-dark` | `#d9dde3` / `#33383f` | Dividers, card borders |
| `$font-family-sans-serif` | `'Archivo', system-ui, …` | All text. Headings weight 650, `font-stretch: 87.5%`. |
| `$h1-font-size` … `$h3-font-size` | 1.75rem, 1.125rem, 1rem | Page title, section title, sub-section |
| `$border-radius` / `-sm` / `-lg` / `-xl` | 6px / 4px / 10px / 16px | Controls / plates / cards and tables / dialogs |
| `$grid-breakpoints` | sm 576, md 768, **lg 1024**, xl 1280, xxl 1400 | The sidebar becomes a drawer below `lg` |
| `$input-btn-padding-y`, `$btn-padding-y` | 40px controls | 44px on touch screens (see `_base.scss`) |
| `$theme-colors` | no `light` / `dark` | The colour modes cover those |

### App tokens (`_tokens.scss`)

| Token | Light | Dark | Use |
|---|---|---|---|
| `--asp-surface` | `#ffffff` | `#1f2226` | Cards, inputs, tables, dialogs, list groups, toasts (Bootstrap's `$card-bg`, `$input-bg`, … point at it) |
| `--asp-border-strong` | `#8a939f` | `#6b7480` | Input, checkbox and secondary-button borders (≥ 3:1) |
| `--asp-focus` / `--asp-focus-ring` | `#7a5100` | `#ffc933` | Focus outline / input focus glow |
| `--asp-violet(-bg)`, `--asp-teal(-bg)` | | | Extra status tones |
| `--asp-chrome-bg` | `#1c1f23` | `#141619` | Sidebar, phone top bar, auth band |
| `--asp-sidebar-width`, `--asp-content-max` | 248px, 1360px | | Shell layout |

### Colour modes

Bootstrap's colour modes are driven by `data-bs-theme` on `<html>`:

- `ThemeService` (`mode` signal: `'light' | 'dark' | 'system'`, `use(mode)`, `resolved` computed) always sets `data-bs-theme="light|dark"`. In `system` mode it follows `prefers-color-scheme` and its changes. The choice is stored in `localStorage['asp.theme']` (inside try/catch).
- An inline script in `index.html` sets the same attribute before first paint, so there is no flash.
- The app chrome (sidebar, phone top bar, the brand row on the auth pages) is always dark: it carries `data-bs-theme="dark"` itself.
- Drop the toggle anywhere: `<app-theme-toggle />` (a `btn-group` with `aria-pressed` buttons).

### Contrast

Every text pair in both modes meets WCAG AA (4.5:1): body, muted and link text on the page, surface, tertiary and sunken backgrounds, every `*-text-emphasis` on its `*-bg-subtle`, status badges, invalid-feedback text, and text on amber and danger buttons. Input borders reach 3:1. `#f2b200` amber is never used for text on light surfaces (1.9:1).

---

## App shell

`Shell` renders a graphite sidebar (brand, `nav nav-pills flex-column app-nav`, language, theme, user, sign out) on desktop. Below 1024px it renders a top bar with a menu button. The same sidebar becomes a Bootstrap `offcanvas-lg offcanvas-start` drawer, with `.show` bound to the `menuOpen` signal and an `offcanvas-backdrop`. A backdrop click, Escape or a nav click closes it. To add a nav item:

```html
@if (auth.hasRole('OWNER')) {
  <a class="nav-link" routerLink="/backoffice" routerLinkActive="active" ariaCurrentWhenActive="page"><app-icon name="briefcase" />{{ 'nav.backoffice' | t }}</a>
}
```

Screens render inside `<main class="app-content">`, which already has the page padding.

---

## Page patterns

**Page header**

```html
<header class="d-flex flex-wrap align-items-end gap-3 mb-4">
  <div class="me-auto">
    <a routerLink="/customers" class="d-inline-block mb-2 small fw-medium text-body-secondary text-decoration-none">{{ 'customers.back' | t }}</a>
    <h1 class="mb-2">{{ 'customers.title' | t }}</h1>
    <p class="text-body-secondary mb-0">Optional one-line description</p>
    <ul class="list-inline d-flex flex-wrap gap-2 column-gap-4 mt-3 mb-0 small text-body-secondary">  <!-- inline facts -->
      <li class="d-inline-flex align-items-center gap-2"><app-icon name="gauge" [size]="16" />84 500 km</li>
    </ul>
  </div>
  <div class="d-flex flex-wrap gap-2">
    <button type="button" class="btn btn-outline-secondary"><app-icon name="refresh" [size]="16" />{{ 'common.refresh' | t }}</button>
    <button type="button" class="btn btn-primary"><app-icon name="plus" [size]="16" />{{ 'customers.new' | t }}</button>
  </div>
</header>
```

**Card and facts**

```html
<section class="card shadow-sm mb-4">
  <div class="card-body">
    <div class="d-flex flex-wrap align-items-center gap-3 mb-3">
      <h2 class="card-title me-auto mb-0">Title</h2>
      <button type="button" class="btn btn-sm btn-outline-secondary">Edit</button>
    </div>
    <dl class="row g-3 mb-0">
      <div class="col-sm-6 col-xl-4"><dt class="small text-body-secondary fw-semibold">{{ 'common.nif' | t }}</dt><dd class="mb-0 text-break">123456789</dd></div>
    </dl>
  </div>
</section>
```

Put a `.row` inside `.card-body` / `.modal-body`, never on the same element (its negative margins would pull the content out of the padding).

**Empty state**

```html
<div class="d-flex flex-column align-items-center gap-2 py-5 text-center text-body-secondary">
  <app-icon name="inbox" [size]="36" class="text-body-tertiary" />
  <p class="fw-semibold text-body mb-0">No work orders yet</p>
  <a class="btn btn-outline-secondary mt-2" routerLink="/check-in">Check in</a>
</div>
```

**Section titles:** `<h2 class="mt-5 mb-3">`.

---

## Which Bootstrap component for what

| Need | Use |
|---|---|
| Primary action | `btn btn-primary` (one per area) |
| Secondary action | `btn btn-outline-secondary` (re-themed in `_base.scss`: surface background, strong border, follows the colour mode) |
| Quiet text action in a table row | `btn btn-sm btn-link`; destructive: `btn btn-sm btn-link text-danger-emphasis` |
| Confirm a destructive action | `btn btn-danger` inside a confirm dialog |
| Icon-only button | `btn btn-ghost btn-icon` + `[attr.aria-label]` (app classes: 40×40, 44×44 on touch) |
| Close button | `btn-close` + `[attr.aria-label]="'common.close' \| t"` (40px hit area) |
| Busy button | `[attr.aria-busy]="busy()"` and `@if (busy()) { <span class="spinner-border spinner-border-sm" aria-hidden="true"></span> }` |
| Form layout | `row g-3` with `col-sm-6`, `col-lg-3`, `col-12`; actions in `col-12 d-flex justify-content-end gap-2` |
| Text, number, email input | `form-label` + `form-control`, linked by `for`/`id` (prefix ids per component, e.g. `ce-name`) |
| Select | `form-select` (`form-select-sm` in table rows) |
| Checkbox | `form-check` > `form-check-input` + `form-check-label` |
| Slider | `form-range` |
| Search box | `input-group` with an `input-group-text` icon |
| Address block | `<fieldset class="border rounded-3 p-3">` + `<legend class="float-none w-auto px-2 mb-0 small fw-semibold text-body-secondary">` |
| Field hint | `form-text` |
| Field error | `[class.is-invalid]` bound to the reactive state (`control.invalid && control.touched`, or the component's `message(field)`) + `<div class="invalid-feedback">` after the control. Group-level errors (address) use `invalid-feedback d-block`. |
| ProblemDetail / page message | `<app-alert>` (Bootstrap `alert alert-{tone}` with an icon). Danger and warning use `role="alert"`, the others `role="status"`. |
| Success message after an action | Toast: `toast-container position-fixed bottom-0 end-0 p-3` > `toast show` with a `btn-close` (see `backoffice-layout.ts`; `Toasts` service) |
| Data table | `table-stack` card (below) |
| Pick one from a list | `list-group` + `list-group-item list-group-item-action` buttons (owner search) |
| Timeline of linked items | `list-group` of `<a class="list-group-item list-group-item-action">` (vehicle history) |
| Section tabs | `nav nav-underline` (backoffice) |
| Proportion bars | `progress` > `progress-bar` with `[style.width.%]`; inside `[data-status]` the bar takes the status colour |
| KPI tiles | `row g-3` > `col-sm-6 col-xl-4` > `card shadow-sm h-100` > `card-body` with label, `.stat-value`, caption |
| Loading | `placeholder-glow` with `placeholder` (`placeholder-lg col-6` for a title, `.placeholder-block w-100` for an 88px card) |
| Tags | `badge` + a tint pair, e.g. `badge bg-info-subtle text-info-emphasis`; roles: owner primary, front desk info, mechanic secondary |
| Modal dialog | Native `<dialog class="modal">` (below) |

---

## Dialog (native `<dialog>` with Bootstrap modal markup)

```html
<dialog #confirm class="modal" aria-labelledby="confirm-title">
  <div class="modal-dialog">                       <!-- + modal-dialog-centered for short confirms -->
    <form class="modal-content" method="dialog">   <!-- or [formGroup] + (ngSubmit) -->
      <div class="modal-header">
        <h2 class="modal-title" id="confirm-title">Delete user?</h2>
        <button type="button" class="btn-close" [attr.aria-label]="'common.close' | t" (click)="confirm.close()"></button>
      </div>
      <div class="modal-body"><p class="mb-0">This cannot be undone.</p></div>
      <div class="modal-footer">
        <button class="btn btn-outline-secondary" value="cancel">Cancel</button>
        <button type="button" class="btn btn-danger" (click)="remove(); confirm.close()">Delete</button>
      </div>
    </form>
  </div>
</dialog>
```

`showModal()` provides the top layer, focus handling, Escape to close and `::backdrop`, so Bootstrap's modal JavaScript is not needed. `_dialog.scss` resets the UA dialog box and shows `dialog.modal[open]`. Dialogs are 560px wide (`$modal-md`).

---

## Data table (stacks on phones)

```html
<div class="card table-stack mb-4">
  <div class="table-responsive">
    <table class="table table-hover align-middle mb-0">
      <thead><tr><th>{{ 'common.name' | t }}</th><th class="text-end">{{ 'workOrder.total' | t }}</th><th></th></tr></thead>
      <tbody>
        @for (c of rows(); track c.id) {
          <tr [class.row-muted]="!c.active">
            <td class="cell-title"><a [routerLink]="['/customers', c.id]">{{ c.name }}</a></td>
            <td class="text-end" [attr.data-label]="'workOrder.total' | t">{{ c.totalCents | money }}</td>
            <td class="cell-actions"><button type="button" class="btn btn-sm btn-link">Edit</button></td>
          </tr>
        } @empty {
          <tr><td colspan="3" class="cell-full"><!-- empty state --></td></tr>
        }
      </tbody>
      <tfoot><tr class="bg-body-tertiary">…</tr></tfoot>   <!-- optional totals -->
    </table>
  </div>
</div>
```

From 576px up it is a framed, hoverable table, and figures use tabular numbers. Below 576px every row becomes a card. Each cell shows its `data-label` as a caption, `.cell-title` becomes the card title and `.cell-actions` the card footer. Empty cells are hidden. `.row-muted` greys out a row (inactive users).

---

## App-specific classes

| Class | What |
|---|---|
| `.app-layout`, `.app-sidebar`, `.app-topbar`, `.app-content`, `.app-nav`, `.app-brand(-mark/-name)`, `.app-avatar` | Shell (`_shell.scss`) |
| `.auth-page`, `.auth-inner` | Sign-in / sign-up frame with the graphite band |
| `.btn-ghost`, `.btn-icon` | Quiet button variant, square icon button |
| `.table-stack`, `.cell-title`, `.cell-actions`, `.cell-full`, `.row-muted` | Responsive table |
| `.status-badge` + `[data-status]` | Status pill with a dot: `<span class="badge status-badge" [attr.data-status]="w.status">` |
| `[data-status]` | Sets `--asp-tone` / `--asp-tone-bg` on any element (badges, board columns, progress bars) |
| `.plate` (+ `.plate-band`, `.plate-stars`, `.plate-number`, `.plate-date`, `.plate-code`) | Host and parts of `<app-plate>`; don't write this markup by hand (see [Number plates](#number-plates)) |
| `.plate-field` | Plate-shaped lookup input (check-in search); `.is-invalid` turns its border red |
| `.plate-input` | Plate text input in vehicle forms: typed text shows upper case |
| `.board`, `.column`, `.column-header`, `.column-empty`, `.wo-card` | Kanban board |
| `.stat-value` | Large KPI figure |
| `.placeholder-block` | 88px placeholder card |
| `.icon` | `<app-icon>` host (1.25em, or `[size]` px) |

| WorkOrderStatus | Tone |
|---|---|
| `DIAGNOSTICS` | info (blue) |
| `AWAITING_APPROVAL` | warning (amber) |
| `AWAITING_PARTS` | violet |
| `IN_PROGRESS` | teal |
| `READY` | success (green) |
| `DELIVERED` | neutral |
| `CANCELLED` | danger, struck through |

**Kanban board:** columns use a status-coloured top border and dot, and cards are `card wo-card` links with a plate. On wide screens the board scrolls sideways inside its own box. Below 576px the columns stack, and an empty column shrinks to one compact row (`.column:has(.column-empty)`). The page itself never scrolls horizontally.

---

## Number plates

### `<app-plate>` — show a plate

Use it everywhere a plate is shown (board cards, vehicle and work-order headers, vehicle lists, dialogs, search results). Pass the plate fields the API returns on vehicles, work orders and board cards:

```html
<app-plate [plate]="v.plateDisplay ?? v.plate" [era]="v.plateEra" [country]="v.registrationCountry" [registrationDate]="v.registrationDate" />
<app-plate … size="sm" />   <!-- board cards, dialog titles -->
```

| Input | Type | |
|---|---|---|
| `plate` | `string | null` | Prefer `plateDisplay`. A stored Portuguese plate (`12AB34`) is formatted `12-AB-34`. `null` shows "—". |
| `era` | `'PT_2020' | 'PT_2005' | 'PT_1992' | 'PT_1937' | null` | Picks the look (below). |
| `country` | ISO alpha-2 | null | Shown on neutral plates and in the label of foreign plates. |
| `registrationDate` | `'YYYY-MM' | null` | The yellow date strip. |
| `size` | `'sm' | 'md' | 'lg'` | 0.875em / 1em / 1.25em of the surrounding font size (default `md`; `md` is 0.8em inside `h1`). |

| Era | Look (`data-kind`) | Date strip (year over month, right) |
|---|---|---|
| `PT_2020` (`AA-00-AA`) | `eu`: white, black characters, blue EU band with the ring of 12 stars and "P" | never |
| `PT_2005` (`00-AA-00`) | `eu` | when `registrationDate` is known |
| `PT_1992` (`00-00-AA`) | `eu` | only when `registrationDate` ≥ `1998-01` |
| `PT_1937` (`AA-00-00`) | `classic`: black, silver characters, no band | never |
| `null` (other countries, or an old plate that fits no layout) | `neutral`: white with a grey country-code tab, plain text | never |

The mapping lives in `plateVariant(era, registrationDate)` (`shared/plate-format.ts`). The plate is CSS only (the stars are a tiny inline SVG), no images or extra fonts: Archivo at `font-stretch: 75%`. Everything is sized in `em`, so a plate follows the text it sits in. A plate keeps its own colours in light and dark mode, like a real plate; the classic black plate has a thin grey inner edge so it stays visible on dark surfaces. The host is `role="img"` with `aria-label` "Plate 12-AB-34" (`plate.label`; foreign plates add the country: `plate.labelCountry`), and its parts are `aria-hidden`.

### `<app-registration-fields>` — enter a plate

Every form that creates or edits a vehicle uses the same three fields: **registration country** (select), **plate** and optional **registration month** (`<input type="month">`). Build the controls with `registrationControls()` and drop the component straight inside the form's `.row` (its host is a `col-12` with its own `.row g-3`, so it adds three aligned columns):

```ts
form = inject(FormBuilder).nonNullable.group({
  ...registrationControls(workshopCountry.country()),          // add: default = workshop country
  // ...registrationControls('PT', { stored: () => this.stored }) // edit: an unchanged old plate is not re-checked
  vin: [''],
});
```
```html
<div class="row g-3">
  <app-registration-fields [controls]="form.controls" prefix="va" col="col-sm-6 col-lg-3" />   <!-- ids va-country, va-plate, va-month -->
  …
</div>
```

- **Default country:** `WorkshopCountry` (`shared/workshop-country.ts`): `load()` reads `GET /workshop/settings` once for owners and the front desk; mechanics and errors keep `PT`. A form only applies it while the user has not touched the country select.
- **Portugal:** the plate is checked live against `AA-00-AA` (2020+), `00-AA-00` (2005–2020), `00-00-AA` (1992–2005) and `AA-00-00` (1937–1992), accepting any case, spaces or hyphens. The error (`error.invalid-plate`, listing the layouts) waits until six characters are typed or the field is left, so it does not flash on the first keystroke. On blur a valid plate is reformatted `XX-XX-XX` upper case. A hint lists the layouts while there is no error.
- **Other countries:** free text, no format check; upper-cased and trimmed on blur.
- **Server errors:** map them with `applyProblem(form, e, VEHICLE_CODE_FIELDS)` (`customer/vehicle-edit-dialog.ts`): `invalid-plate` and `duplicate-plate` show under the plate, `mileage-decrease` under the mileage.
- **Month:** `min="1937-01"`, `max` = this month, and `registrationMonthValidator()` for browsers that render it as a text box.
- **Request:** send `registrationCountry` (upper case) and `registrationDate` (`'YYYY-MM'` or `null`) in `POST /vehicles` and `PUT /vehicles/{id}`.

**Plate search** (check-in): a country select next to the `.plate-field` lookup, the same live check and blur formatting via `registrationControls(country, { plateRequired: false })` and `plateMessage()`. The search still runs with an invalid layout, because plates saved before the check may fit none.

---

## Icons

```html
<app-icon name="plus" />                         <!-- decorative, 1.25em -->
<app-icon name="clock" [size]="14" />            <!-- fixed size in px -->
<app-icon name="search" label="Search" />        <!-- meaningful: role="img" + aria-label -->
```

Available names: `board check-in users user briefcase wrench car log-out menu x sun moon monitor globe refresh plus search chevron-left arrow-right history clock trash inbox alert-circle alert-triangle check-circle info gauge fuel`. To add one, copy the Lucide path data (ISC licence, noted in the file) into `ICONS`.

---

## Bundle size

Bootstrap generates every utility at every breakpoint and every component variant for every theme colour by default. `styles.scss` imports only the modules in use, and `_utilities.scss` is an allow-list. Around the modules that loop (buttons, alerts, list group, tables, grid, modal, offcanvas, validation), `styles.scss` swaps in narrower `$theme-colors` / `$grid-breakpoints` maps, so only the variants used are generated (for example only `.offcanvas-lg`, only `btn-primary` / `btn-danger`). Utilities are not responsive: the shell handles its few breakpoint changes in `_shell.scss`.

The production build is about 504 kB initial (≈ 101 kB CSS, ≈ 15.7 kB transferred); the initial warning budget is 550 kB (error 1 MB). Before adding a utility key, a responsive flag or a theme-colour variant, check `npx ng build`.

Sass `@import` deprecation warnings from Bootstrap 5.3 are silenced in `angular.json` (`stylePreprocessorOptions.sass.silenceDeprecations`).

## Accessibility checklist

- Focus: every focusable element gets a 2px `--asp-focus` outline (amber on graphite chrome).
- Hit targets: controls are 40px high (44px on touch, `pointer: coarse`), small buttons 40px on touch, and `btn-close` has a 40px area.
- Every input has a `<label for>` (or `aria-label` for search and inline selects). Errors use `is-invalid` + `invalid-feedback` and `aria-invalid` in the edit dialogs.
- Contrast: see [Contrast](#contrast).
- Motion: Bootstrap honours `prefers-reduced-motion`, and `_base.scss` also stops the app's own animations.
- Icon-only controls need `aria-label`. Toggle-like buttons use `aria-pressed`, and disclosure buttons use `aria-expanded`.
