package state

import (
	"testing"
)

func TestLocalStoreOperations(t *testing.T) {
	store := &LocalStore{
		Servers: []LocalServer{},
	}

	// 1. Add server
	srv1 := LocalServer{
		Host:       "198.51.100.10",
		VPNPort:    443,
		Protocol:   "vless-reality",
		ServerName: "gateway.icloud.com",
		Alias:      "Node-Frankfurt",
		UserAuth:   "11111111-2222-3333-4444-555555555555",
	}
	store.AddOrUpdateServer(srv1)

	if len(store.Servers) != 1 {
		t.Fatalf("expected 1 server in store, got %d", len(store.Servers))
	}

	// 2. Find server by alias
	found := store.FindServer("Node-Frankfurt")
	if found == nil || found.Host != "198.51.100.10" {
		t.Fatalf("expected to find server by alias, got %+v", found)
	}

	// 3. Find server by host
	foundHost := store.FindServer("198.51.100.10")
	if foundHost == nil || foundHost.Alias != "Node-Frankfurt" {
		t.Fatalf("expected to find server by host, got %+v", foundHost)
	}

	// 4. Update server with new field
	srv1Updated := LocalServer{
		Host:       "198.51.100.10",
		VPNPort:    443,
		Alias:      "Node-Frankfurt",
		ShareLink:  "vless://11111111-2222-3333-4444-555555555555@198.51.100.10:443#Node-Frankfurt",
	}
	store.AddOrUpdateServer(srv1Updated)
	if len(store.Servers) != 1 {
		t.Fatalf("expected still 1 server, got %d", len(store.Servers))
	}
	if store.Servers[0].UserAuth != "11111111-2222-3333-4444-555555555555" {
		t.Errorf("expected UserAuth preserved on update, got %s", store.Servers[0].UserAuth)
	}
	if store.Servers[0].ShareLink != srv1Updated.ShareLink {
		t.Errorf("expected ShareLink updated, got %s", store.Servers[0].ShareLink)
	}

	// 5. Add second server (Hysteria2)
	srv2 := LocalServer{
		Host:       "203.0.113.5",
		VPNPort:    8443,
		Protocol:   "hysteria2",
		Alias:      "Node-Helsinki",
		UserAuth:   "password_hy2_123",
	}
	store.AddOrUpdateServer(srv2)
	if len(store.Servers) != 2 {
		t.Fatalf("expected 2 servers, got %d", len(store.Servers))
	}

	// 6. Remove first server
	removed := store.RemoveServer("Node-Frankfurt")
	if !removed {
		t.Fatalf("expected server to be removed successfully")
	}
	if len(store.Servers) != 1 {
		t.Fatalf("expected 1 server left, got %d", len(store.Servers))
	}
	if store.Servers[0].Alias != "Node-Helsinki" {
		t.Errorf("expected Node-Helsinki left, got %s", store.Servers[0].Alias)
	}
}
