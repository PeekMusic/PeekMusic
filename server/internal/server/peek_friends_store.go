// PeekMusic: storage for PeekParty friends, in the server's bbolt database next to upstream's buckets.

package server

import (
	"bytes"
	"crypto/rand"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"math/big"
	"slices"
	"time"

	"go.etcd.io/bbolt"
)

var (
	// Buckets PeekMusic 1.3.0's server created; the names and value formats stay so existing data keeps working.
	peekFriendsBucket   = []byte("friends")   // friend code -> JSON []string of friend codes; the key exists for every account
	peekUsernamesBucket = []byte("usernames") // friend code -> last name used
	peekLegacyAuth      = []byte("auth_tokens")

	peekAuthBucket        = []byte("peek_auth")         // hex SHA-256 of the auth token -> friend code
	peekRequestsInBucket  = []byte("peek_requests_in")  // to \x00 from -> unix seconds
	peekRequestsOutBucket = []byte("peek_requests_out") // from \x00 to -> unix seconds
	peekMetaBucket        = []byte("peek_meta")         // friend code -> JSON peekAccountMeta
)

var (
	errPeekNoDatabase    = errors.New("friends are not available on this server")
	errPeekUnknownCode   = errors.New("no one has this friend code")
	errPeekOwnCode       = errors.New("that is your own friend code")
	errPeekTooManyOut    = errors.New("too many open friend requests")
	errPeekNoRequest     = errors.New("there is no such friend request")
	errPeekTooManyFriend = errors.New("friend list is full")
)

const (
	peekFriendCodeChars    = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
	peekMaxOutgoing        = 50
	peekMaxFriends         = 500
	peekMinAuthTokenLength = 16
	peekMaxAuthTokenLength = 200
)

type peekAccountMeta struct {
	// V2 is set once the account connected with a client that understands friend requests. Adding a friend
	// whose app is older still works the 1.3.0 way: both become friends at once, since they could never accept.
	V2 bool `json:"v2,omitempty"`
}

type peekStore struct{ db *bbolt.DB }

func (s *Server) peekStore() (*peekStore, error) {
	if s.database == nil || s.database.db == nil {
		return nil, errPeekNoDatabase
	}
	return &peekStore{db: s.database.db}, nil
}

func peekTokenKey(token string) []byte {
	sum := sha256.Sum256([]byte(token))
	return []byte(hex.EncodeToString(sum[:]))
}

func peekPairKey(a, b string) []byte { return []byte(a + "\x00" + b) }

// normalizeFriendCode accepts "a1b2c3d4e", "A1B-2C3-D4E" or with spaces and returns "A1B-2C3-D4E", or "".
func normalizeFriendCode(code string) string {
	clean := make([]byte, 0, 9)
	for i := 0; i < len(code); i++ {
		ch := code[i]
		switch {
		case ch == '-' || ch == ' ':
			continue
		case ch >= 'a' && ch <= 'z':
			ch -= 'a' - 'A'
		case (ch >= 'A' && ch <= 'Z') || (ch >= '0' && ch <= '9'):
		default:
			return ""
		}
		clean = append(clean, ch)
	}
	if len(clean) != 9 {
		return ""
	}
	return fmt.Sprintf("%s-%s-%s", clean[0:3], clean[3:6], clean[6:9])
}

func generateFriendCode() (string, error) {
	out := make([]byte, 9)
	limit := big.NewInt(int64(len(peekFriendCodeChars)))
	for i := range out {
		n, err := rand.Int(rand.Reader, limit)
		if err != nil {
			return "", err
		}
		out[i] = peekFriendCodeChars[n.Int64()]
	}
	return fmt.Sprintf("%s-%s-%s", out[0:3], out[3:6], out[6:9]), nil
}

func peekEnsureBuckets(tx *bbolt.Tx) error {
	for _, name := range [][]byte{peekFriendsBucket, peekUsernamesBucket, peekAuthBucket, peekRequestsInBucket, peekRequestsOutBucket, peekMetaBucket} {
		if _, err := tx.CreateBucketIfNotExists(name); err != nil {
			return err
		}
	}
	return nil
}

// migrate creates the buckets and replaces 1.3.0's plaintext auth tokens with their hashes.
func (p *peekStore) migrate() (int, error) {
	moved := 0
	err := p.db.Update(func(tx *bbolt.Tx) error {
		if err := peekEnsureBuckets(tx); err != nil {
			return err
		}
		legacy := tx.Bucket(peekLegacyAuth)
		if legacy == nil {
			return nil
		}
		auth := tx.Bucket(peekAuthBucket)
		if err := legacy.ForEach(func(token, code []byte) error {
			moved++
			return auth.Put(peekTokenKey(string(token)), slices.Clone(code))
		}); err != nil {
			return err
		}
		return tx.DeleteBucket(peekLegacyAuth)
	})
	return moved, err
}

func readCodes(b *bbolt.Bucket, code string) []string {
	var list []string
	if raw := b.Get([]byte(code)); raw != nil {
		_ = json.Unmarshal(raw, &list)
	}
	return list
}

func writeCodes(b *bbolt.Bucket, code string, list []string) error {
	if list == nil {
		list = []string{}
	}
	data, err := json.Marshal(list)
	if err != nil {
		return err
	}
	return b.Put([]byte(code), data)
}

// authenticate returns the account for token, creating one with a fresh friend code the first time.
func (p *peekStore) authenticate(token, username string, v2 bool) (string, error) {
	var code string
	err := p.db.Update(func(tx *bbolt.Tx) error {
		if err := peekEnsureBuckets(tx); err != nil {
			return err
		}
		auth := tx.Bucket(peekAuthBucket)
		friends := tx.Bucket(peekFriendsBucket)
		key := peekTokenKey(token)
		if existing := auth.Get(key); existing != nil {
			code = string(existing)
		} else {
			for {
				candidate, err := generateFriendCode()
				if err != nil {
					return err
				}
				if friends.Get([]byte(candidate)) == nil {
					code = candidate
					break
				}
			}
			if err := auth.Put(key, []byte(code)); err != nil {
				return err
			}
		}
		if friends.Get([]byte(code)) == nil {
			if err := writeCodes(friends, code, nil); err != nil {
				return err
			}
		}
		if username != "" {
			if err := tx.Bucket(peekUsernamesBucket).Put([]byte(code), []byte(username)); err != nil {
				return err
			}
		}
		if v2 {
			return putMeta(tx, code, peekAccountMeta{V2: true})
		}
		return nil
	})
	return code, err
}

func getMeta(tx *bbolt.Tx, code string) peekAccountMeta {
	var meta peekAccountMeta
	if raw := tx.Bucket(peekMetaBucket).Get([]byte(code)); raw != nil {
		_ = json.Unmarshal(raw, &meta)
	}
	return meta
}

func putMeta(tx *bbolt.Tx, code string, meta peekAccountMeta) error {
	data, err := json.Marshal(meta)
	if err != nil {
		return err
	}
	return tx.Bucket(peekMetaBucket).Put([]byte(code), data)
}

func (p *peekStore) markV2(code string) error {
	return p.db.Update(func(tx *bbolt.Tx) error {
		if err := peekEnsureBuckets(tx); err != nil {
			return err
		}
		return putMeta(tx, code, peekAccountMeta{V2: true})
	})
}

func (p *peekStore) saveUsername(code, username string) error {
	return p.db.Update(func(tx *bbolt.Tx) error {
		if err := peekEnsureBuckets(tx); err != nil {
			return err
		}
		return tx.Bucket(peekUsernamesBucket).Put([]byte(code), []byte(username))
	})
}

type peekSnapshot struct {
	friends   []string
	incoming  []string
	outgoing  []string
	usernames map[string]string
}

func prefixScan(b *bbolt.Bucket, code string) []string {
	var out []string
	prefix := []byte(code + "\x00")
	cur := b.Cursor()
	for k, _ := cur.Seek(prefix); k != nil && bytes.HasPrefix(k, prefix); k, _ = cur.Next() {
		out = append(out, string(k[len(prefix):]))
	}
	return out
}

// snapshot reads the friends, open requests and the names of everyone in them.
func (p *peekStore) snapshot(code string) (peekSnapshot, error) {
	snap := peekSnapshot{usernames: map[string]string{}}
	err := p.db.View(func(tx *bbolt.Tx) error {
		friends := tx.Bucket(peekFriendsBucket)
		if friends == nil {
			return nil
		}
		snap.friends = readCodes(friends, code)
		if in := tx.Bucket(peekRequestsInBucket); in != nil {
			snap.incoming = prefixScan(in, code)
		}
		if out := tx.Bucket(peekRequestsOutBucket); out != nil {
			snap.outgoing = prefixScan(out, code)
		}
		names := tx.Bucket(peekUsernamesBucket)
		for _, list := range [][]string{snap.friends, snap.incoming, snap.outgoing} {
			for _, c := range list {
				if names != nil {
					snap.usernames[c] = string(names.Get([]byte(c)))
				}
			}
		}
		return nil
	})
	return snap, err
}

func (p *peekStore) friends(code string) []string {
	var list []string
	_ = p.db.View(func(tx *bbolt.Tx) error {
		if b := tx.Bucket(peekFriendsBucket); b != nil {
			list = readCodes(b, code)
		}
		return nil
	})
	return list
}

func (p *peekStore) username(code string) string {
	var name string
	_ = p.db.View(func(tx *bbolt.Tx) error {
		if b := tx.Bucket(peekUsernamesBucket); b != nil {
			name = string(b.Get([]byte(code)))
		}
		return nil
	})
	return name
}

func (p *peekStore) areFriends(a, b string) bool {
	return slices.Contains(p.friends(a), b)
}

func linkFriendsTx(tx *bbolt.Tx, a, b string) error {
	friends := tx.Bucket(peekFriendsBucket)
	listA, listB := readCodes(friends, a), readCodes(friends, b)
	if !slices.Contains(listA, b) {
		if len(listA) >= peekMaxFriends {
			return errPeekTooManyFriend
		}
		listA = append(listA, b)
	}
	if !slices.Contains(listB, a) {
		if len(listB) >= peekMaxFriends {
			return errPeekTooManyFriend
		}
		listB = append(listB, a)
	}
	if err := writeCodes(friends, a, listA); err != nil {
		return err
	}
	return writeCodes(friends, b, listB)
}

func unlinkFriendsTx(tx *bbolt.Tx, a, b string) (bool, error) {
	friends := tx.Bucket(peekFriendsBucket)
	listA, listB := readCodes(friends, a), readCodes(friends, b)
	was := slices.Contains(listA, b) || slices.Contains(listB, a)
	if friends.Get([]byte(a)) != nil {
		if err := writeCodes(friends, a, slices.DeleteFunc(listA, func(c string) bool { return c == b })); err != nil {
			return false, err
		}
	}
	if friends.Get([]byte(b)) != nil {
		if err := writeCodes(friends, b, slices.DeleteFunc(listB, func(c string) bool { return c == a })); err != nil {
			return false, err
		}
	}
	return was, nil
}

func deleteRequestTx(tx *bbolt.Tx, from, to string) (bool, error) {
	in, out := tx.Bucket(peekRequestsInBucket), tx.Bucket(peekRequestsOutBucket)
	existed := in.Get(peekPairKey(to, from)) != nil || out.Get(peekPairKey(from, to)) != nil
	if err := in.Delete(peekPairKey(to, from)); err != nil {
		return false, err
	}
	if err := out.Delete(peekPairKey(from, to)); err != nil {
		return false, err
	}
	return existed, nil
}

// peekAddResult says what add_friend did.
type peekAddResult int

const (
	peekAddedFriend   peekAddResult = iota // friends now (or already were)
	peekAddedRequest                       // a request waits for the other person
	peekAddedAccepted                      // the other person had already asked: friends now
)

// addFriend befriends from and to: as a request when to's app knows requests, at once when it cannot accept
// one (PeekMusic 1.3.0), and at once when to had already sent from a request.
func (p *peekStore) addFriend(from, to string) (peekAddResult, error) {
	var result peekAddResult
	err := p.db.Update(func(tx *bbolt.Tx) error {
		if err := peekEnsureBuckets(tx); err != nil {
			return err
		}
		friends := tx.Bucket(peekFriendsBucket)
		if friends.Get([]byte(to)) == nil {
			return errPeekUnknownCode
		}
		if slices.Contains(readCodes(friends, from), to) {
			result = peekAddedFriend
			return nil
		}
		if tx.Bucket(peekRequestsInBucket).Get(peekPairKey(from, to)) != nil {
			if _, err := deleteRequestTx(tx, to, from); err != nil {
				return err
			}
			result = peekAddedAccepted
			return linkFriendsTx(tx, from, to)
		}
		if !getMeta(tx, to).V2 {
			result = peekAddedFriend
			return linkFriendsTx(tx, from, to)
		}
		if len(prefixScan(tx.Bucket(peekRequestsOutBucket), from)) >= peekMaxOutgoing {
			return errPeekTooManyOut
		}
		now := []byte(fmt.Sprint(time.Now().Unix()))
		if err := tx.Bucket(peekRequestsInBucket).Put(peekPairKey(to, from), now); err != nil {
			return err
		}
		result = peekAddedRequest
		return tx.Bucket(peekRequestsOutBucket).Put(peekPairKey(from, to), now)
	})
	return result, err
}

// acceptRequest turns from's request to me into a friendship.
func (p *peekStore) acceptRequest(me, from string) error {
	return p.db.Update(func(tx *bbolt.Tx) error {
		if err := peekEnsureBuckets(tx); err != nil {
			return err
		}
		existed, err := deleteRequestTx(tx, from, me)
		if err != nil {
			return err
		}
		if !existed {
			return errPeekNoRequest
		}
		return linkFriendsTx(tx, me, from)
	})
}

// removeAny ends whatever links me and other: a friendship, my request to them, or theirs to me.
func (p *peekStore) removeAny(me, other string) (bool, error) {
	changed := false
	err := p.db.Update(func(tx *bbolt.Tx) error {
		if err := peekEnsureBuckets(tx); err != nil {
			return err
		}
		was, err := unlinkFriendsTx(tx, me, other)
		if err != nil {
			return err
		}
		out, err := deleteRequestTx(tx, me, other)
		if err != nil {
			return err
		}
		in, err := deleteRequestTx(tx, other, me)
		if err != nil {
			return err
		}
		changed = was || out || in
		return nil
	})
	return changed, err
}

// deleteAccount forgets code completely and returns everyone who had it as a friend or in a request.
func (p *peekStore) deleteAccount(code string) ([]string, error) {
	var affected []string
	err := p.db.Update(func(tx *bbolt.Tx) error {
		if err := peekEnsureBuckets(tx); err != nil {
			return err
		}
		friends := tx.Bucket(peekFriendsBucket)
		for _, other := range readCodes(friends, code) {
			if _, err := unlinkFriendsTx(tx, code, other); err != nil {
				return err
			}
			affected = append(affected, other)
		}
		for _, other := range prefixScan(tx.Bucket(peekRequestsOutBucket), code) {
			if _, err := deleteRequestTx(tx, code, other); err != nil {
				return err
			}
			affected = append(affected, other)
		}
		for _, other := range prefixScan(tx.Bucket(peekRequestsInBucket), code) {
			if _, err := deleteRequestTx(tx, other, code); err != nil {
				return err
			}
			affected = append(affected, other)
		}
		auth := tx.Bucket(peekAuthBucket)
		var tokens [][]byte
		if err := auth.ForEach(func(k, v []byte) error {
			if string(v) == code {
				tokens = append(tokens, slices.Clone(k))
			}
			return nil
		}); err != nil {
			return err
		}
		for _, k := range tokens {
			if err := auth.Delete(k); err != nil {
				return err
			}
		}
		for _, b := range [][]byte{peekFriendsBucket, peekUsernamesBucket, peekMetaBucket} {
			if err := tx.Bucket(b).Delete([]byte(code)); err != nil {
				return err
			}
		}
		return nil
	})
	slices.Sort(affected)
	return slices.Compact(affected), err
}
