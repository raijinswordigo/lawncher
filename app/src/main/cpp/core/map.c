#include "map.h"
#include <stdlib.h>
#include <string.h>

typedef struct MapNode {
	void *key;
	void *value;
	struct MapNode *next;
	struct MapNode *prev;
} MapNode;

struct Map {
	int type;
	MapNode *head;
	MapNode *tail;
	size_t count;
};

static int key_match(const Map *m, const MapNode *node, void *key)
{
	if (m->type == 1)
		return node->key == key;
	if (m->type == 2)
		return key && node->key &&
		       strcmp((const char *)node->key, (const char *)key) == 0;
	return 0;
}

static void unlink_node(Map *m, MapNode *n)
{
	if (n->prev) n->prev->next = n->next;
	else m->head = n->next;

	if (n->next) n->next->prev = n->prev;
	else m->tail = n->prev;

	if (m->count) m->count--;
	free(n);
}

Map *Map_Create(int type)
{
	if (type != 1 && type != 2) return NULL;

	Map *m = malloc(sizeof(Map));
	if (!m) return NULL;

	m->type  = type;
	m->head  = NULL;
	m->tail  = NULL;
	m->count = 0;
	return m;
}

void Map_Set(Map *m, void *key, void *value)
{
	if (!m) return;

	for (MapNode *n = m->head; n; n = n->next) {
		if (key_match(m, n, key)) {
			n->value = value;
			return;
		}
	}

	MapNode *node = malloc(sizeof(MapNode));
	if (!node) return;

	node->key   = key;
	node->value = value;
	node->next  = NULL;
	node->prev  = m->tail;

	if (m->tail) m->tail->next = node;
	else m->head = node;

	m->tail = node;
	m->count++;
}

void *Map_Get(Map *m, void *key)
{
	if (!m) return NULL;

	for (MapNode *n = m->head; n; n = n->next)
		if (key_match(m, n, key)) return n->value;

	return NULL;
}

void Map_Remove(Map *m, void *key)
{
	if (!m) return;

	for (MapNode *n = m->head; n; ) {
		MapNode *next = n->next;
		if (key_match(m, n, key)) {
			unlink_node(m, n);
			return;
		}
		n = next;
	}
}

void Map_Clear(Map *m)
{
	if (!m) return;

	MapNode *n = m->head;
	while (n) {
		MapNode *next = n->next;
		free(n);
		n = next;
	}
	m->head  = NULL;
	m->tail  = NULL;
	m->count = 0;
}

void Map_Destroy(Map *m)
{
	if (!m) return;
	Map_Clear(m);
	free(m);
}

void Map_SetOwned(Map *m, const char *key, const char *value)
{
	if (!m || m->type != 2 || !key) return;

	char *vcopy = value ? strdup(value) : strdup("");
	if (!vcopy) return;

	for (MapNode *n = m->head; n; n = n->next) {
		if (key_match(m, n, (void *)key)) {
			free(n->value);
			n->value = vcopy;
			return;
		}
	}

	char *kcopy = strdup(key);
	if (!kcopy) {
		free(vcopy);
		return;
	}

	MapNode *node = malloc(sizeof(MapNode));
	if (!node) {
		free(kcopy);
		free(vcopy);
		return;
	}

	node->key   = kcopy;
	node->value = vcopy;
	node->next  = NULL;
	node->prev  = m->tail;

	if (m->tail) m->tail->next = node;
	else m->head = node;

	m->tail = node;
	m->count++;
}

void Map_RemoveOwned(Map *m, const char *key)
{
	if (!m || m->type != 2 || !key) return;

	for (MapNode *n = m->head; n; ) {
		MapNode *next = n->next;
		if (key_match(m, n, (void *)key)) {
			free(n->key);
			free(n->value);
			unlink_node(m, n);
			return;
		}
		n = next;
	}
}

void Map_ClearOwned(Map *m)
{
	if (!m || m->type != 2) return;

	MapNode *n = m->head;
	while (n) {
		MapNode *next = n->next;
		free(n->key);
		free(n->value);
		free(n);
		n = next;
	}
	m->head  = NULL;
	m->tail  = NULL;
	m->count = 0;
}

void Map_DestroyOwned(Map *m)
{
	if (!m) return;
	Map_ClearOwned(m);
	free(m);
}

size_t Map_Count(const Map *m)
{
	return m ? m->count : 0;
}
