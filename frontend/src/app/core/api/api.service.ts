import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';

import {
  Board,
  CheckInUpdateRequest,
  Customer,
  CustomerRequest,
  LineType,
  Mechanic,
  MechanicAssignment,
  User,
  Vehicle,
  VehicleCreateRequest,
  VehicleUpdateRequest,
  WorkOrder,
  WorkOrderStatus,
} from './models';

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
  updateCustomer(id: string, body: CustomerRequest) {
    return this.http.put<Customer>(`${API}/customers/${id}`, body);
  }
  vehiclesOf(customerId: string) {
    return this.http.get<Vehicle[]>(`${API}/customers/${customerId}/vehicles`);
  }
  createVehicle(body: VehicleCreateRequest) {
    return this.http.post<Vehicle>(`${API}/vehicles`, body);
  }
  updateVehicle(id: string, body: VehicleUpdateRequest) {
    return this.http.put<Vehicle>(`${API}/vehicles/${id}`, body);
  }
  transferVehicle(id: string, customerId: string, version: number) {
    return this.http.put<Vehicle>(`${API}/vehicles/${id}/owner`, { customerId, version });
  }
  vehicleByPlate(plate: string) {
    return this.http.get<Vehicle>(`${API}/vehicles`, { params: new HttpParams().set('plate', plate) });
  }
  /** Exact 17-character VIN, any case. 400 `invalid-vin` for another length, 404 `vehicle-not-found`. */
  vehicleByVin(vin: string) {
    return this.http.get<Vehicle>(`${API}/vehicles`, { params: new HttpParams().set('vin', vin) });
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
  updateCheckIn(id: string, body: CheckInUpdateRequest) {
    return this.http.put<WorkOrder>(`${API}/work-orders/${id}/check-in`, body);
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
  /** Gives the work order to a mechanic or to nobody; replacing or removing one needs a reason (`reason-required`). */
  assign(id: string, mechanicId: string | null, reason: string | null) {
    return this.http.put<WorkOrder>(`${API}/work-orders/${id}/mechanic`, { mechanicId, reason });
  }
  /** Current mechanic and the history of changes with reasons (owner, front desk). */
  mechanicAssignment(id: string) {
    return this.http.get<MechanicAssignment>(`${API}/work-orders/${id}/mechanic`);
  }
  /** Active mechanics of the workshop (owner, front desk). */
  mechanics() {
    return this.http.get<Mechanic[]>(`${API}/mechanics`);
  }

  // Users
  users() {
    return this.http.get<User[]>(`${API}/users`);
  }
}
