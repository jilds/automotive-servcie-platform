import { Pipe, PipeTransform } from '@angular/core';

const EUR = new Intl.NumberFormat('pt-PT', { style: 'currency', currency: 'EUR' });

/** Formats integer cents as euros, for example 10148 as 101,48 €. */
@Pipe({ name: 'money' })
export class MoneyPipe implements PipeTransform {
  transform(cents: number | null | undefined): string {
    return cents == null ? '' : EUR.format(cents / 100);
  }
}
