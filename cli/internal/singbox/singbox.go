package singbox

import (
	"crypto/ecdh"
	"crypto/rand"
	"encoding/base64"
	"encoding/hex"
	"fmt"
	"io"
)

const (
	// DefaultSingBoxVersion is the pinned sing-box release version
	DefaultSingBoxVersion = "1.9.7"

	ProtocolVLESSReality = "vless-reality"
	ProtocolHysteria2    = "hysteria2"
)

// Supported architectures and their verified SHA256 sums
var ReleaseChecksums = map[string]map[string]string{
	"1.9.7": {
		"amd64": "d0f4a31b70a194df28d101a05025592a6d26098fa57ffd4f75481968b0fb7878",
		"arm64": "0324cb742d28a8b9d5e331fa3e726eb5d23470a7917ecb0227e0acb2643a66b3",
	},
}

// Recommended camouflage SNI domains (all verified to support TLS 1.3 & HTTP/2)
var RecommendedDomains = []string{
	"gateway.icloud.com",
	"www.microsoft.com",
	"dl.google.com",
	"swdist.apple.com",
	"addons.mozilla.org",
}

// KeyPair holds Reality Curve25519 keys encoded in RawURL Base64
type KeyPair struct {
	PrivateKey string
	PublicKey  string
}

// GenerateRealityKeyPair generates an X25519 keypair for Reality mask
func GenerateRealityKeyPair() (*KeyPair, error) {
	curve := ecdh.X25519()
	priv, err := curve.GenerateKey(rand.Reader)
	if err != nil {
		return nil, fmt.Errorf("failed to generate X25519 private key: %w", err)
	}

	pub := priv.PublicKey()

	return &KeyPair{
		PrivateKey: base64.RawURLEncoding.EncodeToString(priv.Bytes()),
		PublicKey:  base64.RawURLEncoding.EncodeToString(pub.Bytes()),
	}, nil
}

// GenerateUUID generates a cryptographically secure RFC 4122 v4 UUID
func GenerateUUID() (string, error) {
	var uuid [16]byte
	if _, err := io.ReadFull(rand.Reader, uuid[:]); err != nil {
		return "", err
	}
	// Set version 4 (bits 4-7 of 7th byte = 0100)
	uuid[6] = (uuid[6] & 0x0f) | 0x40
	// Set variant to RFC 4122 (bits 6-7 of 9th byte = 10)
	uuid[8] = (uuid[8] & 0x3f) | 0x80

	return fmt.Sprintf("%08x-%04x-%04x-%04x-%012x",
		uuid[0:4], uuid[4:6], uuid[6:8], uuid[8:10], uuid[10:16]), nil
}

// GenerateShortID generates a random 8-byte hexadecimal string
func GenerateShortID() (string, error) {
	b := make([]byte, 8)
	if _, err := rand.Read(b); err != nil {
		return "", err
	}
	return hex.EncodeToString(b), nil
}

// GeneratePassword generates a cryptographically secure random password for Hysteria2
func GeneratePassword() (string, error) {
	b := make([]byte, 16)
	if _, err := io.ReadFull(rand.Reader, b); err != nil {
		return "", err
	}
	return hex.EncodeToString(b), nil
}

// DownloadURL returns the official release download URL for a given version and arch
func DownloadURL(version, arch string) (string, error) {
	switch arch {
	case "x86_64", "amd64":
		arch = "amd64"
	case "aarch64", "arm64":
		arch = "arm64"
	default:
		return "", fmt.Errorf("unsupported server architecture: %s (supported: amd64, arm64)", arch)
	}

	return fmt.Sprintf("https://github.com/SagerNet/sing-box/releases/download/v%s/sing-box-%s-linux-%s.tar.gz",
		version, version, arch), nil
}

// ExpectedChecksum returns the expected SHA256 checksum for the release archive
func ExpectedChecksum(version, arch string) (string, error) {
	switch arch {
	case "x86_64", "amd64":
		arch = "amd64"
	case "aarch64", "arm64":
		arch = "arm64"
	default:
		return "", fmt.Errorf("unsupported architecture: %s", arch)
	}

	verMap, ok := ReleaseChecksums[version]
	if !ok {
		return "", fmt.Errorf("no pinned checksums for sing-box version %s", version)
	}

	sum, ok := verMap[arch]
	if !ok {
		return "", fmt.Errorf("no checksum for arch %s in version %s", arch, version)
	}

	return sum, nil
}
