import { CatalogueService } from './catalogue.models';

/** Services of one category; `category` is null for services without one. */
export interface CategoryGroup {
  category: string | null;
  services: CatalogueService[];
}

/**
 * Groups services by category, sorted in the UI language ignoring case and accents ("manutencao" goes with
 * "Manutenção"); services without a category come last. Inside a group the services are sorted by name.
 */
export function groupByCategory(services: CatalogueService[], lang: string): CategoryGroup[] {
  const collator = new Intl.Collator(lang, { sensitivity: 'base' });
  const same = (a: string | null, b: string | null) => (a === null || b === null ? a === b : collator.compare(a, b) === 0);
  const groups: CategoryGroup[] = [];
  for (const service of services) {
    const category = service.category?.trim() || null;
    let group = groups.find((g) => same(g.category, category));
    if (!group) {
      group = { category, services: [] };
      groups.push(group);
    }
    group.services.push(service);
  }
  groups.sort((a, b) => (a.category === null ? 1 : b.category === null ? -1 : collator.compare(a.category, b.category)));
  groups.forEach((g) => g.services.sort((a, b) => collator.compare(a.name, b.name)));
  return groups;
}
