import type {
  EndpointResponse,
  FileItem,
  FriendStatusResponse,
  HealthResponse,
  LoginResponse,
  WebPairingCodeResponse,
  WebPairingStatusResponse,
  Message,
  StorageListResponse
} from "../../../shared/api-contracts/types";

export type {
  EndpointResponse,
  FileItem,
  FriendStatusResponse,
  HealthResponse,
  LoginResponse,
  WebPairingCodeResponse,
  WebPairingStatusResponse,
  Message,
  StorageListResponse
};

export interface AuthSession extends LoginResponse {
  signedInAt: string;
}
