import { CatalogueCoreKey } from './catalogue-core.en';

/** Portuguese (Portugal) error codes of the catalogue. */
export const catalogueCorePtPT: Record<CatalogueCoreKey, string> = {
  'error.service-not-found': 'Esse serviço não está no catálogo.',
  'error.package-not-found': 'Esse pacote não está no catálogo.',
  'error.service-inactive': 'Esse serviço já não se faz.',
  'error.package-inactive': 'Esse pacote já não está disponível.',
  'error.package-empty': 'Um pacote precisa de pelo menos um serviço.',
};
