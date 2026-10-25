export const plans = {
  names: {
    adventurer: 'Aventurier',
    hero: 'Héros',
    'dungeon-master': 'Maître du Donjon',
    legendary: 'Légendaire',
  },
  taglines: {
    adventurer: 'Pour découvrir la table',
    hero: 'Pour jouer plus à l’aise',
    'dungeon-master': 'Pour mener plusieurs campagnes',
    legendary: 'Pour ne plus rien compter',
  },
  limits: {
    activeAdventuresAsMaster: 'Aventures actives en tant que MJ',
    charactersWithoutAdventure: 'Personnages hors aventure',
    spellBooks: 'Grimoires',
    magicItems: 'Objets magiques',
    masterNotesPerAdventure: 'Notes du MJ par aventure',
    historicalNotesPerAdventure: 'Notes d’historique par aventure',
    charactersPerPlayerAndAdventure: 'Personnages par joueur et par aventure',
    npcsPerAdventure: 'PNJ par aventure',
    messagesPerAdventure: 'Messages conservés par aventure',
  },
  features: {
    characterPictures: 'Images de personnage',
    video: 'Visio',
    equippedTable: 'Table équipée',
    earlyAccess: 'Accès anticipé',
    prioritySupport: 'Support prioritaire',
  },
  badges: {
    founder: 'Fondateur',
    dungeonMaster: 'MJ',
    legend: 'Légende',
  },
  mostChosen: 'Le plus choisi',
  monthly: 'Mensuel',
  yearly: 'Annuel',
  twoMonthsFree: '2 mois offerts',
  perMonth: '/ mois',
  perYear: '/ an',
  free: 'Gratuit',
  limitReached: (limitLabel: string, max: number | undefined) =>
    max === undefined
      ? `Limite atteinte : ${limitLabel}.`
      : `Limite atteinte : ${limitLabel} (${max} au maximum).`,
  featureMissing: (featureLabel: string) => `Cette fonction n’est pas incluse : ${featureLabel}.`,
  upgradeTo: (planName: string) => `L’offre ${planName} le permet.`,
  askMaster: (planName: string) =>
    `Demande à ton MJ : il lui faudrait l’offre ${planName} pour le permettre.`,
  seeOffers: 'Voir les offres',
};
