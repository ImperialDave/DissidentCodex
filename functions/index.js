const { onCall, HttpsError } = require('firebase-functions/v2/https');
const { onDocumentUpdated, onDocumentCreated } = require('firebase-functions/v2/firestore');
const { initializeApp } = require('firebase-admin/app');
const { getFirestore, FieldValue, Timestamp } = require('firebase-admin/firestore');
const { AccessToken, RoomServiceClient } = require('livekit-server-sdk');

initializeApp();
const db = getFirestore();
const GIPHY_BASE = 'https://api.giphy.com/v1/gifs';
const START_FEN = 'rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1';

function gameIdFor(uidA, uidB) {
  const [a, b] = uidA < uidB ? [uidA, uidB] : [uidB, uidA];
  return `chess_${a}_${b}`;
}

async function fetchUser(uid) {
  const snap = await db.collection('users').doc(uid).get();
  if (!snap.exists) return null;
  return snap.data();
}

function parseGiphyResponse(body) {
  const root = JSON.parse(body);
  const data = root.data || [];
  return data.map((obj) => {
    const images = obj.images || {};
    const fixed = images.fixed_height || {};
    const preview = images.preview_gif || images.fixed_height_small || fixed;
    const fullUrl = fixed.url || '';
    const previewUrl = preview.url || fullUrl;
    return {
      id: obj.id || '',
      previewUrl,
      fullUrl,
    };
  }).filter((item) => item.id && item.fullUrl);
}

exports.searchGiphy = onCall({ cors: true }, async (request) => {
    if (!request.auth) {
      throw new HttpsError('unauthenticated', 'Sign in required.');
    }

    const apiKey = process.env.GIPHY_API_KEY || '';
    if (!apiKey) {
      throw new HttpsError('failed-precondition', 'GIPHY_API_KEY is not configured.');
    }

    const query = String(request.data?.query || '').trim();
    const limit = Math.min(Math.max(Number(request.data?.limit) || 24, 1), 50);
    const endpoint = query
      ? `${GIPHY_BASE}/search?api_key=${encodeURIComponent(apiKey)}&q=${encodeURIComponent(query)}&limit=${limit}&rating=pg-13`
      : `${GIPHY_BASE}/trending?api_key=${encodeURIComponent(apiKey)}&limit=${limit}&rating=pg-13`;

    const response = await fetch(endpoint);
    if (!response.ok) {
      const errBody = await response.text();
      console.error('Giphy API error', response.status, errBody);
      throw new HttpsError('internal', `Giphy API error (${response.status})`);
    }

    const body = await response.text();
    return { items: parseGiphyResponse(body) };
});

exports.startChessGame = onCall({ cors: true }, async (request) => {
  if (!request.auth) {
    throw new HttpsError('unauthenticated', 'Sign in required.');
  }

  const me = request.auth.uid;
  const opponentUid = String(request.data?.opponentUid || '').trim();
  if (!opponentUid || opponentUid === me) {
    throw new HttpsError('invalid-argument', 'Invalid opponent.');
  }

  const myUser = await fetchUser(me);
  const oppUser = await fetchUser(opponentUid);
  if (!myUser) throw new HttpsError('failed-precondition', 'Profile missing.');
  if (!oppUser) throw new HttpsError('not-found', 'Opponent not found.');

  const gameId = gameIdFor(me, opponentUid);
  const ref = db.collection('chessGames').doc(gameId);
  const existing = await ref.get();
  const now = Timestamp.now();

  if (existing.exists) {
    const current = existing.data();
    if (current.status === 'active') {
      return { gameId, ...current };
    }
    if (current.status === 'pending') {
      await ref.update({ status: 'active', updatedAt: now });
      const updated = await ref.get();
      return { gameId, ...updated.data() };
    }
    if (current.status === 'finished' || current.status === 'declined') {
      const whiteName = current.whiteUid === me
        ? (myUser.displayName || 'Player')
        : (oppUser.displayName || 'Player');
      const blackName = current.blackUid === me
        ? (myUser.displayName || 'Player')
        : (oppUser.displayName || 'Player');
      await ref.update({
        playerUids: [current.whiteUid, current.blackUid],
        whiteUid: current.whiteUid,
        blackUid: current.blackUid,
        whiteName,
        blackName,
        challengerUid: me,
        status: 'active',
        fen: START_FEN,
        turn: 'w',
        winnerUid: FieldValue.delete(),
        result: FieldValue.delete(),
        eloApplied: false,
        updatedAt: now,
      });
      const updated = await ref.get();
      return { gameId, ...updated.data() };
    }
  }

  const game = {
    playerUids: [me, opponentUid],
    whiteUid: me,
    blackUid: opponentUid,
    whiteName: myUser.displayName || 'Player',
    blackName: oppUser.displayName || 'Player',
    challengerUid: me,
    status: 'active',
    fen: START_FEN,
    turn: 'w',
    createdAt: now,
    updatedAt: now,
  };
  await ref.set(game);
  return { gameId, ...game };
});

const DEFAULT_ELO = 1200;

function kFactor(gamesPlayed) {
  return gamesPlayed < 30 ? 32 : 16;
}

function expectedScore(myElo, oppElo) {
  return 1 / (1 + Math.pow(10, (oppElo - myElo) / 400));
}

function newElo(oldElo, oppElo, score, gamesPlayed) {
  const k = kFactor(gamesPlayed);
  const e = expectedScore(oldElo, oppElo);
  return Math.round(oldElo + k * (score - e));
}

function chessStatsFromUser(data) {
  return {
    chessElo: data?.chessElo ?? DEFAULT_ELO,
    chessGamesPlayed: data?.chessGamesPlayed ?? 0,
    chessWins: data?.chessWins ?? 0,
    chessLosses: data?.chessLosses ?? 0,
    chessDraws: data?.chessDraws ?? 0,
  };
}

async function upsertNotification(userId, payload) {
  const notifsRef = db.collection('users').doc(userId).collection('notifications');
  if ((payload.type === 'CHESS_TURN' || payload.type === 'VOICE_INCOMING') && payload.targetId) {
    const existing = await notifsRef
      .where('type', '==', payload.type)
      .where('targetId', '==', payload.targetId)
      .where('read', '==', false)
      .limit(1)
      .get();
    if (!existing.empty) {
      await existing.docs[0].ref.update({
        title: payload.title,
        body: payload.body,
        actorUid: payload.actorUid || null,
        actorName: payload.actorName || null,
        createdAt: Timestamp.now(),
      });
      return;
    }
  }
  await notifsRef.add({
    ...payload,
    read: false,
    createdAt: Timestamp.now(),
  });
}

async function pruneOldNotifications(userId) {
  const cutoff = Timestamp.fromMillis(Date.now() - 30 * 24 * 60 * 60 * 1000);
  const old = await db.collection('users').doc(userId).collection('notifications')
    .where('createdAt', '<', cutoff)
    .limit(50)
    .get();
  if (old.empty) return;
  const batch = db.batch();
  old.docs.forEach((doc) => batch.delete(doc.ref));
  await batch.commit();
}

exports.onChessGameUpdated = onDocumentUpdated('chessGames/{gameId}', async (event) => {
  const before = event.data.before.data();
  const after = event.data.after.data();
  if (!before || !after) return;

  const gameId = event.params.gameId;

  if (before.status === 'active' && after.status === 'active' && before.turn !== after.turn) {
    const recipientUid = after.turn === 'w' ? after.whiteUid : after.blackUid;
    const moverUid = after.turn === 'w' ? after.blackUid : after.whiteUid;
    const moverName = after.turn === 'w' ? after.blackName : after.whiteName;
    if (recipientUid && moverUid && recipientUid !== moverUid) {
      await upsertNotification(recipientUid, {
        type: 'CHESS_TURN',
        title: 'Your move',
        body: `${moverName || 'Opponent'} made a move — your turn`,
        actorUid: moverUid,
        actorName: moverName || 'Opponent',
        targetId: gameId,
      });
      await pruneOldNotifications(recipientUid);
    }
  }

  if (before.status !== 'finished' && after.status === 'finished' && !after.eloApplied) {
    const whiteUid = after.whiteUid;
    const blackUid = after.blackUid;
    if (!whiteUid || !blackUid) return;

    const [whiteSnap, blackSnap] = await Promise.all([
      db.collection('users').doc(whiteUid).get(),
      db.collection('users').doc(blackUid).get(),
    ]);
    const whiteStats = chessStatsFromUser(whiteSnap.data());
    const blackStats = chessStatsFromUser(blackSnap.data());

    let whiteScore = 0.5;
    let blackScore = 0.5;
    const result = after.result || '';
    if (result === 'stalemate' || result === 'draw') {
      whiteScore = 0.5;
      blackScore = 0.5;
    } else if (after.winnerUid === whiteUid) {
      whiteScore = 1;
      blackScore = 0;
    } else if (after.winnerUid === blackUid) {
      whiteScore = 0;
      blackScore = 1;
    } else {
      return;
    }

    const newWhiteElo = newElo(
      whiteStats.chessElo,
      blackStats.chessElo,
      whiteScore,
      whiteStats.chessGamesPlayed
    );
    const newBlackElo = newElo(
      blackStats.chessElo,
      whiteStats.chessElo,
      blackScore,
      blackStats.chessGamesPlayed
    );

    const batch = db.batch();
    const whiteRef = db.collection('users').doc(whiteUid);
    const blackRef = db.collection('users').doc(blackUid);
    const gameRef = db.collection('chessGames').doc(gameId);

    const whiteUpdate = {
      chessElo: newWhiteElo,
      chessGamesPlayed: whiteStats.chessGamesPlayed + 1,
    };
    const blackUpdate = {
      chessElo: newBlackElo,
      chessGamesPlayed: blackStats.chessGamesPlayed + 1,
    };

    if (whiteScore === 1) {
      whiteUpdate.chessWins = whiteStats.chessWins + 1;
      blackUpdate.chessLosses = blackStats.chessLosses + 1;
    } else if (blackScore === 1) {
      blackUpdate.chessWins = blackStats.chessWins + 1;
      whiteUpdate.chessLosses = whiteStats.chessLosses + 1;
    } else {
      whiteUpdate.chessDraws = whiteStats.chessDraws + 1;
      blackUpdate.chessDraws = blackStats.chessDraws + 1;
    }

    batch.update(whiteRef, whiteUpdate);
    batch.update(blackRef, blackUpdate);
    batch.update(gameRef, { eloApplied: true });
    await batch.commit();
  }
});

exports.onPostLiked = onDocumentCreated('users/{userId}/likedPosts/{postId}', async (event) => {
  const likerUid = event.params.userId;
  const postId = event.params.postId;
  const postSnap = await db.collection('posts').doc(postId).get();
  if (!postSnap.exists) return;
  const post = postSnap.data();
  const authorId = post.authorId;
  if (!authorId || authorId === likerUid) return;

  const liker = await fetchUser(likerUid);
  const likerName = liker?.displayName || 'Someone';
  await upsertNotification(authorId, {
    type: 'POST_LIKE',
    title: 'New like',
    body: `${likerName} liked your post`,
    actorUid: likerUid,
    actorName: likerName,
    targetId: postId,
  });
  await pruneOldNotifications(authorId);
});

exports.onCommentCreated = onDocumentCreated('comments/{commentId}', async (event) => {
  const comment = event.data.data();
  if (!comment) return;
  const postId = comment.postId;
  const authorId = comment.authorId;
  if (!postId || !authorId) return;

  const commenterName = comment.authorName || 'Someone';
  const parentCommentId = comment.parentCommentId;

  if (parentCommentId) {
    const parentSnap = await db.collection('comments').doc(parentCommentId).get();
    if (parentSnap.exists) {
      const parentAuthorId = parentSnap.data().authorId;
      if (parentAuthorId && parentAuthorId !== authorId) {
        await upsertNotification(parentAuthorId, {
          type: 'COMMENT_REPLY',
          title: 'New reply',
          body: `${commenterName} replied to your comment`,
          actorUid: authorId,
          actorName: commenterName,
          targetId: postId,
        });
        await pruneOldNotifications(parentAuthorId);
      }
    }
  }

  const postSnap = await db.collection('posts').doc(postId).get();
  if (!postSnap.exists) return;
  const postAuthorId = postSnap.data().authorId;
  if (!postAuthorId || postAuthorId === authorId) return;
  if (parentCommentId) {
    const parentSnap = await db.collection('comments').doc(parentCommentId).get();
    if (parentSnap.exists && parentSnap.data().authorId === postAuthorId) {
      return;
    }
  }

  await upsertNotification(postAuthorId, {
    type: 'POST_COMMENT',
    title: 'New comment',
    body: `${commenterName} commented on your post`,
    actorUid: authorId,
    actorName: commenterName,
    targetId: postId,
  });
  await pruneOldNotifications(postAuthorId);
});

// --- Voice (LiveKit Cloud) ---

const VOICE_MAX_TOPIC = 25;
const VOICE_MAX_GROUP = 25;

function getLiveKitConfig() {
  const apiKey = process.env.LIVEKIT_API_KEY;
  const apiSecret = process.env.LIVEKIT_API_SECRET;
  const url = process.env.LIVEKIT_URL;
  if (!apiKey || !apiSecret || !url) {
    throw new HttpsError(
      'failed-precondition',
      'LiveKit is not configured on Cloud Functions. Create functions/.env from .env.example, then run: npm run deploy:voice'
    );
  }
  return { apiKey, apiSecret, url };
}

function parseDmMemberIds(roomId, uid) {
  if (!roomId.startsWith('dm_')) return null;
  const parts = roomId.slice(3).split('_').filter(Boolean);
  if (parts.length !== 2 || !parts.includes(uid)) return null;
  return parts;
}

async function fetchUserRole(uid) {
  const snap = await db.collection('users').doc(uid).get();
  if (!snap.exists) return 'USER';
  return String(snap.data().role || 'USER').toUpperCase();
}

function isModeratorRole(role) {
  return role === 'MOD' || role === 'ADMIN' || role === 'FOUNDER';
}

async function assertVoiceRoomAccess(uid, chatRoomId) {
  const snap = await db.collection('chatRooms').doc(chatRoomId).get();
  if (!snap.exists) {
    throw new HttpsError('not-found', 'Chat room not found.');
  }
  const room = snap.data();
  const role = await fetchUserRole(uid);

  if (room.voiceLocked === true && !isModeratorRole(role)) {
    throw new HttpsError('permission-denied', 'Voice is locked in this room.');
  }

  if (room.type === 'topic') {
    return { room, voiceType: 'topic' };
  }
  if (room.type === 'dm') {
    const members = room.memberIds || [];
    if (members.includes(uid) || parseDmMemberIds(chatRoomId, uid)) {
      return { room, voiceType: 'dm' };
    }
    throw new HttpsError('permission-denied', 'Not a participant in this conversation.');
  }
  if (room.type === 'group') {
    if ((room.memberIds || []).includes(uid)) {
      return { room, voiceType: 'group' };
    }
    throw new HttpsError('permission-denied', 'Not a member of this group.');
  }
  throw new HttpsError('invalid-argument', 'Unsupported room type for voice.');
}

async function loadVoiceSession(sessionId) {
  const snap = await db.collection('voiceSessions').doc(sessionId).get();
  if (!snap.exists) {
    throw new HttpsError('not-found', 'Voice session not found.');
  }
  return { id: snap.id, ...snap.data() };
}

function isVoiceParticipant(session, uid) {
  const participants = session.participants || {};
  return uid in participants || session.createdBy === uid || session.calleeUid === uid;
}

exports.createVoiceToken = onCall({ cors: true }, async (request) => {
  try {
    if (!request.auth) {
      throw new HttpsError('unauthenticated', 'Sign in required.');
    }

    const uid = request.auth.uid;
    const sessionId = String(request.data?.sessionId || '').trim();
    if (!sessionId) {
      throw new HttpsError('invalid-argument', 'sessionId is required.');
    }

    const session = await loadVoiceSession(sessionId);
    if (session.status === 'ended') {
      throw new HttpsError('failed-precondition', 'This voice session has ended.');
    }
    if (!isVoiceParticipant(session, uid)) {
      const { voiceType } = await assertVoiceRoomAccess(uid, session.chatRoomId);
      if (voiceType === 'dm') {
        throw new HttpsError('permission-denied', 'You are not part of this call.');
      }
    } else {
      await assertVoiceRoomAccess(uid, session.chatRoomId);
    }

    const displayName = String(request.data?.displayName || 'User').trim().slice(0, 50) || 'User';
    const { apiKey, apiSecret, url } = getLiveKitConfig();
    const roomName = session.livekitRoom || session.chatRoomId;

    const at = new AccessToken(apiKey, apiSecret, {
      identity: uid,
      name: displayName,
      ttl: '1h',
    });
    at.addGrant({
      roomJoin: true,
      room: roomName,
      canPublish: true,
      canSubscribe: true,
      canPublishData: true,
    });

    return {
      token: await at.toJwt(),
      url,
      roomName,
    };
  } catch (err) {
    if (err instanceof HttpsError) throw err;
    console.error('createVoiceToken failed', err);
    const msg = String(err?.message || err || 'Failed to create voice token');
    if (!process.env.LIVEKIT_API_KEY || !process.env.LIVEKIT_API_SECRET || !process.env.LIVEKIT_URL) {
      throw new HttpsError(
        'failed-precondition',
        'LiveKit is not configured on Cloud Functions. Deploy with functions/.env (npm run deploy:voice).'
      );
    }
    throw new HttpsError('internal', msg);
  }
});

exports.endVoiceSession = onCall({ cors: true }, async (request) => {
  try {
    if (!request.auth) {
      throw new HttpsError('unauthenticated', 'Sign in required.');
    }

    const uid = request.auth.uid;
    const sessionId = String(request.data?.sessionId || '').trim();
    if (!sessionId) {
      throw new HttpsError('invalid-argument', 'sessionId is required.');
    }

    const session = await loadVoiceSession(sessionId);
    await assertVoiceRoomAccess(uid, session.chatRoomId);

    const role = await fetchUserRole(uid);
    const canEnd =
      session.createdBy === uid ||
      session.calleeUid === uid ||
      isVoiceParticipant(session, uid) ||
      isModeratorRole(role);

    if (!canEnd) {
      throw new HttpsError('permission-denied', 'Cannot end this session.');
    }

    const { apiKey, apiSecret, url } = getLiveKitConfig();
    const roomName = session.livekitRoom || session.chatRoomId;
    const httpUrl = url.replace(/^wss:\/\//, 'https://').replace(/^ws:\/\//, 'http://');

    try {
      const roomClient = new RoomServiceClient(httpUrl, apiKey, apiSecret);
      await roomClient.deleteRoom(roomName);
    } catch (err) {
      console.warn('LiveKit deleteRoom (may already be empty)', err?.message || err);
    }

    await db.collection('voiceSessions').doc(sessionId).update({
      status: 'ended',
      endedAt: Timestamp.now(),
      endedBy: uid,
    });

    await db.collection('chatRooms').doc(session.chatRoomId).update({
      activeVoiceSessionId: FieldValue.delete(),
    }).catch(() => {});

    return { ok: true };
  } catch (err) {
    if (err instanceof HttpsError) throw err;
    console.error('endVoiceSession failed', err);
    throw new HttpsError('internal', err?.message || 'Failed to end voice session');
  }
});

exports.removeVoiceParticipant = onCall({ cors: true }, async (request) => {
  if (!request.auth) {
    throw new HttpsError('unauthenticated', 'Sign in required.');
  }

  const uid = request.auth.uid;
  const sessionId = String(request.data?.sessionId || '').trim();
  const targetUid = String(request.data?.targetUid || '').trim();
  if (!sessionId || !targetUid) {
    throw new HttpsError('invalid-argument', 'sessionId and targetUid are required.');
  }

  const role = await fetchUserRole(uid);
  const session = await loadVoiceSession(sessionId);
  const { voiceType } = await assertVoiceRoomAccess(uid, session.chatRoomId);

  const canKick =
    isModeratorRole(role) ||
    (voiceType === 'group' && session.createdBy === uid);

  if (!canKick) {
    throw new HttpsError('permission-denied', 'No permission to remove participants.');
  }

  const { apiKey, apiSecret, url } = getLiveKitConfig();
  const roomName = session.livekitRoom || session.chatRoomId;
  const roomClient = new RoomServiceClient(url.replace('wss://', 'https://'), apiKey, apiSecret);

  try {
    await roomClient.removeParticipant(roomName, targetUid);
  } catch (err) {
    console.warn('removeParticipant', err?.message || err);
  }

  await db.collection('voiceSessions').doc(sessionId).update({
    [`participants.${targetUid}.leftAt`]: Timestamp.now(),
  });

  return { ok: true };
});

async function markVoiceIncomingRead(calleeUid, sessionId) {
  if (!calleeUid || !sessionId) return;
  const notifsRef = db.collection('users').doc(calleeUid).collection('notifications');
  const existing = await notifsRef
    .where('type', '==', 'VOICE_INCOMING')
    .where('targetId', '==', sessionId)
    .where('read', '==', false)
    .limit(1)
    .get();
  if (existing.empty) return;
  await existing.docs[0].ref.update({ read: true });
}

exports.onVoiceSessionCreated = onDocumentCreated('voiceSessions/{sessionId}', async (event) => {
  const data = event.data?.data();
  if (!data) return;

  const sessionId = event.params.sessionId;
  if (data.voiceType !== 'dm' || data.status !== 'ringing' || !data.calleeUid) return;

  const caller = await fetchUser(data.createdBy);
  const callerName = caller?.displayName || 'Someone';

  await upsertNotification(data.calleeUid, {
    type: 'VOICE_INCOMING',
    title: 'Incoming voice call',
    body: `${callerName} is calling you`,
    actorUid: data.createdBy || null,
    actorName: callerName,
    targetId: sessionId,
  });
  await pruneOldNotifications(data.calleeUid);
});

exports.onVoiceSessionUpdated = onDocumentUpdated('voiceSessions/{sessionId}', async (event) => {
  const before = event.data.before.data();
  const after = event.data.after.data();
  if (!before || !after) return;

  const sessionId = event.params.sessionId;
  if (before.status === 'ringing' && after.status !== 'ringing' && after.calleeUid) {
    await markVoiceIncomingRead(after.calleeUid, sessionId);
  }
});