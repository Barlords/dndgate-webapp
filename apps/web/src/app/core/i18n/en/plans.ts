import type { Dictionary } from '../dictionary';

export const plans: Dictionary['plans'] = {
  names: {
    adventurer: 'Adventurer',
    hero: 'Hero',
    'dungeon-master': 'Dungeon Master',
    legendary: 'Legendary',
  },
  taglines: {
    adventurer: 'To discover the table',
    hero: 'To play with more room',
    'dungeon-master': 'To run several campaigns',
    legendary: 'To stop counting',
  },
  limits: {
    activeAdventuresAsMaster: 'Active adventures as GM',
    charactersWithoutAdventure: 'Characters outside an adventure',
    spellBooks: 'Spell books',
    magicItems: 'Magic items',
    masterNotesPerAdventure: 'GM notes per adventure',
    historicalNotesPerAdventure: 'History notes per adventure',
    charactersPerPlayerAndAdventure: 'Characters per player and adventure',
    npcsPerAdventure: 'NPCs per adventure',
    messagesPerAdventure: 'Messages kept per adventure',
  },
  features: {
    characterPictures: 'Character pictures',
    video: 'Video call',
    equippedTable: 'Equipped table',
    earlyAccess: 'Early access',
    prioritySupport: 'Priority support',
  },
  badges: {
    founder: 'Founder',
    dungeonMaster: 'GM',
    legend: 'Legend',
  },
  mostChosen: 'Most chosen',
  monthly: 'Monthly',
  yearly: 'Yearly',
  twoMonthsFree: '2 months free',
  perMonth: '/ month',
  perYear: '/ year',
  free: 'Free',
  limitReached: (limitLabel, max) =>
    max === undefined
      ? `Limit reached: ${limitLabel}.`
      : `Limit reached: ${limitLabel} (${max} at most with your plan).`,
  featureMissing: (featureLabel) => `Your plan does not include: ${featureLabel}.`,
  upgradeTo: (planName) => `The ${planName} plan allows it.`,
  askMaster: (planName) => `Ask your GM: they would need the ${planName} plan to allow it.`,
  seeOffers: 'See the plans',
};
