// The tests read the site in French, its main language, whatever the language of the machine
// that runs them (the interface follows the browser's language).
Object.defineProperty(navigator, 'language', { value: 'fr-FR', configurable: true });
