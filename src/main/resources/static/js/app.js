document.addEventListener("DOMContentLoaded", startApp);

async function startApp() {
    await renderLoginView();
    bindLayoutEvents();

    try {
        const restored = await restoreSessionFromCookie();

        if (restored) {
            await enterApplication();
            startTokenExpirationWatcher();
            return;
        }

        showLoginView();
    } catch (error) {
        clearStoredSession();
        showLoginView();
    }
}