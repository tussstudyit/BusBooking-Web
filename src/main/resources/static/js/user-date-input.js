(function () {
  function pad(value) {
    return String(value).padStart(2, "0");
  }

  function isoToDisplay(value) {
    const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(String(value || "").trim());
    if (!match) {
      return "";
    }
    return `${match[3]}/${match[2]}/${match[1].slice(-2)}`;
  }

  function displayToIso(value) {
    const match = /^(\d{1,2})\/(\d{1,2})\/(\d{2}|\d{4})$/.exec(String(value || "").trim());
    if (!match) {
      return null;
    }
    const day = Number(match[1]);
    const month = Number(match[2]);
    const rawYear = Number(match[3]);
    const year = match[3].length === 2 ? 2000 + rawYear : rawYear;
    const date = new Date(year, month - 1, day);
    if (date.getFullYear() !== year || date.getMonth() !== month - 1 || date.getDate() !== day) {
      return null;
    }
    return `${year}-${pad(month)}-${pad(day)}`;
  }

  function bindDateInput(input) {
    const target = document.getElementById(input.dataset.dateInput || "");
    if (!target) {
      return;
    }
    if (!input.value && target.value) {
      input.value = isoToDisplay(target.value);
    }
    input.addEventListener("input", () => input.setCustomValidity(""));
    const form = input.form;
    if (!form) {
      return;
    }
    form.addEventListener("submit", (event) => {
      const raw = input.value.trim();
      if (!raw) {
        target.value = "";
        if (input.required) {
          input.setCustomValidity("Nhap ngay theo dinh dang dd/MM/yy");
          input.reportValidity();
          event.preventDefault();
        }
        return;
      }
      const iso = displayToIso(raw);
      if (!iso) {
        input.setCustomValidity("Nhap ngay theo dinh dang dd/MM/yy");
        input.reportValidity();
        event.preventDefault();
        return;
      }
      input.setCustomValidity("");
      target.value = iso;
    });
  }

  document.addEventListener("DOMContentLoaded", () => {
    document.querySelectorAll("[data-date-input]").forEach(bindDateInput);
  });
})();
