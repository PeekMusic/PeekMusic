import re

with open('internal/server/codec.go', 'r') as f:
    content = f.read()

# Add toProtoMessage cases
to_proto = """	case *ServerCapabilitiesPayload:
		return &pb.ServerCapabilities{SupportsProtobuf: p.SupportsProtobuf, SupportsCompression: p.SupportsCompression, ServerVersion: p.ServerVersion}, nil
	case *AuthenticatePayload:
		return &pb.AuthenticatePayload{AuthToken: p.AuthToken, CurrentUsername: p.CurrentUsername}, nil
	case *AddFriendPayload:
		return &pb.AddFriendPayload{FriendCode: p.FriendCode}, nil
	case *RemoveFriendPayload:
		return &pb.RemoveFriendPayload{FriendCode: p.FriendCode}, nil
	case *AuthSuccessPayload:
		return &pb.AuthSuccessPayload{FriendCode: p.FriendCode}, nil
	case *FriendsStatusPayload:
		pbPayload := &pb.FriendsStatusPayload{}
		if p.Friends != nil {
			pbPayload.Friends = make([]*pb.FriendInfo, len(p.Friends))
			for i, friend := range p.Friends {
				f := friend
				pbPayload.Friends[i] = friendInfoToProto(&f)
			}
		}
		return pbPayload, nil
	case *FriendPresenceUpdatePayload:
		pbPayload := &pb.FriendPresenceUpdatePayload{}
		if p.Friend != nil {
			pbPayload.Friend = friendInfoToProto(p.Friend)
		}
		return pbPayload, nil
	case *FriendAddedPayload:
		return &pb.FriendAddedPayload{FriendCode: p.FriendCode, Success: p.Success, ErrorMessage: p.ErrorMessage}, nil"""

content = content.replace("	case *ServerCapabilitiesPayload:\n		return &pb.ServerCapabilities{SupportsProtobuf: p.SupportsProtobuf, SupportsCompression: p.SupportsCompression, ServerVersion: p.ServerVersion}, nil", to_proto)


from_proto = """	case MsgTypeClientCapabilities:
		var pb pb.ClientCapabilities
		if err := proto.Unmarshal(data, &pb); err != nil {
			return nil, err
		}
		return &ClientCapabilitiesPayload{SupportsProtobuf: pb.SupportsProtobuf, SupportsCompression: pb.SupportsCompression, ClientVersion: pb.ClientVersion}, nil
	case MsgTypeAuthenticate:
		var pbb pb.AuthenticatePayload
		if err := proto.Unmarshal(data, &pbb); err != nil {
			return nil, err
		}
		return &AuthenticatePayload{AuthToken: pbb.AuthToken, CurrentUsername: pbb.CurrentUsername}, nil
	case MsgTypeAddFriend:
		var pbb pb.AddFriendPayload
		if err := proto.Unmarshal(data, &pbb); err != nil {
			return nil, err
		}
		return &AddFriendPayload{FriendCode: pbb.FriendCode}, nil
	case MsgTypeRemoveFriend:
		var pbb pb.RemoveFriendPayload
		if err := proto.Unmarshal(data, &pbb); err != nil {
			return nil, err
		}
		return &RemoveFriendPayload{FriendCode: pbb.FriendCode}, nil"""

content = content.replace("	case MsgTypeClientCapabilities:\n		var pb pb.ClientCapabilities\n		if err := proto.Unmarshal(data, &pb); err != nil {\n			return nil, err\n		}\n		return &ClientCapabilitiesPayload{SupportsProtobuf: pb.SupportsProtobuf, SupportsCompression: pb.SupportsCompression, ClientVersion: pb.ClientVersion}, nil", from_proto)


helpers = """	return pbState
}

func friendInfoToProto(f *FriendInfo) *pb.FriendInfo {
	return &pb.FriendInfo{
		FriendCode:     f.FriendCode,
		Username:       f.Username,
		IsOnline:       f.IsOnline,
		ActiveRoomCode: f.ActiveRoomCode,
	}
}

func protoToFriendInfo(p *pb.FriendInfo) *FriendInfo {
	return &FriendInfo{
		FriendCode:     p.FriendCode,
		Username:       p.Username,
		IsOnline:       p.IsOnline,
		ActiveRoomCode: p.ActiveRoomCode,
	}
}"""
content = content.replace("	return pbState\n}", helpers)

decode_payload = """	case *ClientCapabilitiesPayload:
		p, ok := payload.(*ClientCapabilitiesPayload)
		if !ok {
			return fmt.Errorf("payload type mismatch: expected ClientCapabilitiesPayload, got %T", payload)
		}
		*t = *p
	case *AuthenticatePayload:
		p, ok := payload.(*AuthenticatePayload)
		if !ok {
			return fmt.Errorf("payload type mismatch: expected AuthenticatePayload, got %T", payload)
		}
		*t = *p
	case *AddFriendPayload:
		p, ok := payload.(*AddFriendPayload)
		if !ok {
			return fmt.Errorf("payload type mismatch: expected AddFriendPayload, got %T", payload)
		}
		*t = *p
	case *RemoveFriendPayload:
		p, ok := payload.(*RemoveFriendPayload)
		if !ok {
			return fmt.Errorf("payload type mismatch: expected RemoveFriendPayload, got %T", payload)
		}
		*t = *p"""

content = content.replace("	case *ClientCapabilitiesPayload:\n		p, ok := payload.(*ClientCapabilitiesPayload)\n		if !ok {\n			return fmt.Errorf(\"payload type mismatch: expected ClientCapabilitiesPayload, got %T\", payload)\n		}\n		*t = *p", decode_payload)

with open('internal/server/codec.go', 'w') as f:
    f.write(content)
