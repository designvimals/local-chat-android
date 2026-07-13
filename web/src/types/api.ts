import type {
  EndpointResponse,
  FileItem,
  FriendStatusResponse,
  HealthResponse,
  LoginResponse,
  Message,
  StorageListResponse
} from "../../../shared/api-contracts/types";

export type {
  EndpointResponse,
  FileItem,
  FriendStatusResponse,
  HealthResponse,
  LoginResponse,
  Message,
  StorageListResponse
};

export interface AuthSession extends LoginResponse {
  signedInAt: string;
}
