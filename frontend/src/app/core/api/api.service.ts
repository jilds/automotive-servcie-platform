import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';

import { Board, Customer, LineType, User, Vehicle, WorkOrder, WorkOrderStatus } from './models';

const API = '/api/v1';

@Injectable({ providedIn: 'root' })
export class ApiService {
  private readonly http = inject(HttpClient);

  // Customers and vehicles
  customers(q?: string) {
    const params = q ? new HttpParams().set('q', q) : undefined;
    return this.http.get<Customer[]>(`${API}/customers`, { params });
  }
  customer(id: string) {
    return this.http.get<Customer>(`${API}/customers/${id}`);
  }
  createCustomer(body: Partial<Customer>) {
    return this.http.post<Customer>(`${API}/customers`, body);
  }
  vehiclesOf(customerId: string) {
    return this.http.get<Vehicle[]>(`${API}/customers/${customerId}/vehicles`);
  }
  createVehicle(body: Partial<Vehicle>) {
    return this.http.post<Vehicle>(`${API}/vehicles`, body);
  }
  vehicleByPlate(plate: string) {
    return this.http.get<Vehicle>(`${API}/vehicles`, { params: new HttpParams().set('plate', plate) });
  }
  vehicle(id: string) {
    return this.http.get<Vehicle>(`${API}/vehicles/${id}`);
  }
  vehicleHistory(id: string) {
    return this.http.get<WorkOrder[]>(`${API}/vehicles/${id}/work-orders`);
  }

  // Work orders
  board() {
    return this.http.get<Board>(`${API}/work-orders/board`);
  }
  workOrder(id: string) {
    return this.http.get<WorkOrder>(`${API}/work-orders/${id}`);
  }
  checkIn(body: {
    vehicleId: string;
    mileageIn?: number | null;
    fuelLevel?: number | null;
    complaint?: string | null;
    damageNotes?: string | null;
    assignedMechanicId?: string | null;
  }) {
    return this.http.post<WorkOrder>(`${API}/work-orders`, body);
  }
  addLine(
    id: string,
    body: { type: LineType; description: string; quantity: number; unitPriceCents: number; discountPercent?: number; vatRate: number },
  ) {
    return this.http.post<WorkOrder>(`${API}/work-orders/${id}/lines`, body);
  }
  removeLine(id: string, lineId: string) {
    return this.http.delete<WorkOrder>(`${API}/work-orders/${id}/lines/${lineId}`);
  }
  move(id: string, status: WorkOrderStatus) {
    return this.http.patch<WorkOrder>(`${API}/work-orders/${id}/status`, { status });
  }
  assign(id: string, mechanicId: string | null) {
    return this.http.put<WorkOrder>(`${API}/work-orders/${id}/mechanic`, { mechanicId });
  }

  // Users
  users() {
    return this.http.get<User[]>(`${API}/users`);
  }
}
