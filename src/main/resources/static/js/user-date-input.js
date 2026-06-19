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

  function addDaysIso(value, days) {
    const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(String(value || "").trim());
    if (!match) {
      return "";
    }
    const date = new Date(Number(match[1]), Number(match[2]) - 1, Number(match[3]));
    date.setDate(date.getDate() + days);
    return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
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

  function selectedOption(select) {
    return select.options[select.selectedIndex] || null;
  }

  function ensureOption(select, sourceOption) {
    if (!sourceOption || !sourceOption.value) {
      return;
    }
    const exists = Array.from(select.options).some(option => option.value === sourceOption.value);
    if (exists) {
      return;
    }
    select.add(new Option(sourceOption.text, sourceOption.value));
  }

  function bindRouteSwap(button) {
    const form = button.closest("form");
    if (!form) {
      return;
    }
    const origin = form.querySelector('select[name="origin"]');
    const destination = form.querySelector('select[name="destination"]');
    if (!origin || !destination) {
      return;
    }
    button.addEventListener("click", () => {
      const originOption = selectedOption(origin);
      const destinationOption = selectedOption(destination);
      ensureOption(origin, destinationOption);
      ensureOption(destination, originOption);
      const originValue = origin.value;
      const destinationValue = destination.value;
      origin.value = destinationValue;
      destination.value = originValue;
      origin.dispatchEvent(new Event("change", { bubbles: true }));
      destination.dispatchEvent(new Event("change", { bubbles: true }));
    });
  }

  function bindTripSearch(form) {
    const returnGroup = form.querySelector("[data-return-date]");
    const tripTypeInputs = Array.from(form.querySelectorAll('input[name="tripType"]'));
    if (!returnGroup || tripTypeInputs.length === 0) {
      return;
    }
    const returnDisplay = returnGroup.querySelector("[data-date-input]");
    const returnTarget = returnDisplay ? document.getElementById(returnDisplay.dataset.dateInput || "") : null;
    const departTarget = form.querySelector('input[name="tripDate"]');

    function isRoundTrip() {
      return tripTypeInputs.some(input => input.checked && input.value === "roundTrip");
    }

    function syncReturnDate() {
      if (!returnTarget || returnTarget.value) {
        return;
      }
      const fallback = addDaysIso(departTarget?.value, 1);
      if (!fallback) {
        return;
      }
      returnTarget.value = fallback;
      if (returnDisplay && !returnDisplay.value) {
        returnDisplay.value = isoToDisplay(fallback);
      }
    }

    function refreshTripType() {
      const roundTrip = isRoundTrip();
      form.classList.toggle("is-round-trip", roundTrip);
      form.classList.toggle("is-one-way", !roundTrip);
      returnGroup.hidden = !roundTrip;
      returnGroup.querySelectorAll("input").forEach(input => {
        input.disabled = !roundTrip;
      });
      if (returnDisplay) {
        returnDisplay.required = roundTrip && returnDisplay.dataset.requiredWhenRound === "true";
        if (roundTrip) {
          syncReturnDate();
        }
      }
    }

    tripTypeInputs.forEach(input => input.addEventListener("change", refreshTripType));
    refreshTripType();
  }

  document.addEventListener("DOMContentLoaded", () => {
    document.querySelectorAll("[data-date-input]").forEach(bindDateInput);
    document.querySelectorAll("[data-swap-route]").forEach(bindRouteSwap);
    document.querySelectorAll("[data-trip-search]").forEach(bindTripSearch);
  });
})();
