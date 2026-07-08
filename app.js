const form = document.getElementById("lookup");
const userInput = document.getElementById("user");
const statusEl = document.getElementById("status");
const list = document.getElementById("repos");
const selection = document.getElementById("selection");
const selectedName = document.getElementById("selected-name");
const selectedLink = document.getElementById("selected-link");

form.addEventListener("submit", async (e) => {
  e.preventDefault();
  const user = userInput.value.trim();
  if (!user) return;

  statusEl.textContent = `Loading repositories for ${user}…`;
  list.hidden = true;
  list.innerHTML = "";
  selection.hidden = true;

  try {
    const res = await fetch(
      `https://api.github.com/users/${encodeURIComponent(user)}/repos?per_page=100&sort=updated`,
      { headers: { Accept: "application/vnd.github+json" } }
    );
    if (!res.ok) throw new Error(`GitHub returned ${res.status}`);
    const repos = await res.json();

    if (!repos.length) {
      statusEl.textContent = `No public repositories found for ${user}.`;
      return;
    }

    statusEl.textContent = `${repos.length} repositor${repos.length === 1 ? "y" : "ies"} found.`;
    for (const repo of repos) {
      const li = document.createElement("li");
      li.setAttribute("role", "option");
      li.dataset.fullName = repo.full_name;
      li.dataset.url = repo.html_url;

      const left = document.createElement("div");
      const name = document.createElement("div");
      name.className = "repo-name";
      name.textContent = repo.name;
      const desc = document.createElement("div");
      desc.className = "repo-desc";
      desc.textContent = repo.description || "";
      left.appendChild(name);
      if (repo.description) left.appendChild(desc);

      const right = document.createElement("div");
      right.className = "repo-desc";
      right.textContent = repo.private ? "private" : "public";

      li.append(left, right);
      li.addEventListener("click", () => select(li));
      list.appendChild(li);
    }
    list.hidden = false;
  } catch (err) {
    statusEl.textContent = `Failed to load: ${err.message}`;
  }
});

function select(li) {
  for (const item of list.querySelectorAll("li")) {
    item.setAttribute("aria-selected", item === li ? "true" : "false");
  }
  selectedName.textContent = li.dataset.fullName;
  selectedLink.href = li.dataset.url;
  selection.hidden = false;
}
