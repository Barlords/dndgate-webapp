import type { Dictionary } from '../dictionary';

export const legal: Dictionary['legal'] = {
  navLabel: 'Terms',
  terms: {
    title: 'Terms of sale',
    draftNotice:
      'This text is a draft to be validated: it holds no legal content yet and does not stand as terms of sale.',
    toComplete: 'To be completed',
    sections: {
      publisher: 'Publisher',
      purpose: 'Purpose',
      offersAndPrices: 'Offers and prices',
      durationAndRenewal: 'Duration and renewal',
      payment: 'Payment',
      cancellation: 'Cancellation',
      withdrawal: 'Right of withdrawal and waiver',
      messageHistoryDeletion: 'Automatic deletion of the message history',
      personalData: 'Personal data',
      mediation: 'Mediation',
      governingLaw: 'Governing law',
    },
    back: 'Back to the offers',
  },
};
