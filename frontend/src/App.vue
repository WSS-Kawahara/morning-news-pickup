<script setup>
import { ref, computed, onMounted } from 'vue'

// 画面に表示するデータ。値が変わると画面も自動で書き換わる
const news = ref(null)
const loading = ref(true)
const errorMessage = ref('')

// 更新ボタン用の状態
const refreshing = ref(false)
const refreshError = ref('')

// 今選ばれているタブ（ジャンルのid）。データを受け取ったら1つ目のジャンルを選ぶ
const activeGenreId = ref('')

// 選ばれているジャンルのデータ。見つからなければ1つ目のジャンル
const activeGenre = computed(() => {
  if (!news.value || news.value.genres.length === 0) {
    return null
  }
  return (
    news.value.genres.find((genre) => genre.id === activeGenreId.value) ??
    news.value.genres[0]
  )
})

// サーバーからニュースを受け取る共通処理
async function loadNews(url, options) {
  const response = await fetch(url, options)
  if (!response.ok) {
    throw new Error(`HTTP ${response.status}`)
  }
  return await response.json()
}

// 受け取ったデータを画面に反映する。選んでいるタブがなければ1つ目を選ぶ
function applyNews(data) {
  news.value = data
  const exists = data.genres.some((genre) => genre.id === activeGenreId.value)
  if (!exists && data.genres.length > 0) {
    activeGenreId.value = data.genres[0].id
  }
}

// 画面が表示されたタイミングで取得する
onMounted(async () => {
  try {
    applyNews(await loadNews('/api/news'))
  } catch (e) {
    console.error(e)
    errorMessage.value = 'ニュースの取得に失敗しました'
  } finally {
    loading.value = false
  }
})

// 更新ボタン：必ず取り直す
async function refresh() {
  refreshing.value = true
  refreshError.value = ''
  try {
    applyNews(await loadNews('/api/news/refresh', { method: 'POST' }))
  } catch (e) {
    console.error(e)
    refreshError.value = '更新に失敗しました'
  } finally {
    refreshing.value = false
  }
}

// ジャンル内の記事数の合計（タブに表示する件数）
function countItems(genre) {
  return genre.sources.reduce((sum, source) => sum + source.items.length, 0)
}

// 日時の表示形式（日本時間）
const fullFormat = new Intl.DateTimeFormat('ja-JP', {
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
  timeZone: 'Asia/Tokyo',
})

const shortFormat = new Intl.DateTimeFormat('ja-JP', {
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
  timeZone: 'Asia/Tokyo',
})

// "2026-11-06T07:47:04.847786500Z" → "2026/11/06 16:47"（withYear=false なら "11/06 16:47"）
function formatDateTime(iso, withYear = true) {
  // 小数点以下の秒が長すぎるとブラウザによって読めないので、3桁までに切り詰める
  const normalized = iso.replace(/(\.\d{3})\d+/, '$1')
  const format = withYear ? fullFormat : shortFormat
  return format.format(new Date(normalized))
}
</script>

<template>
  <div class="page">
    <header class="header">
      <div class="container header-inner">
        <div class="header-top">
          <div class="header-text">
            <h1 class="title">朝のニュースまとめ</h1>
            <p class="subtitle">各サイトの注目記事をまとめて表示</p>
          </div>

          <div class="header-actions">
            <span v-if="refreshError" class="refresh-error">{{ refreshError }}</span>
            <span v-if="news" class="updated">最終更新 {{ formatDateTime(news.fetchedAt) }}</span>
            <button
              type="button"
              class="refresh-button"
              :disabled="loading || refreshing"
              @click="refresh"
            >
              {{ refreshing ? '更新中…' : '更新' }}
            </button>
          </div>
        </div>

        <nav v-if="news" class="tabs" aria-label="ジャンル">
          <button
            v-for="genre in news.genres"
            :key="genre.id"
            type="button"
            class="tab"
            :class="{ active: genre.id === activeGenre?.id }"
            :aria-pressed="genre.id === activeGenre?.id"
            @click="activeGenreId = genre.id"
          >
            {{ genre.label }}
            <span class="tab-count">{{ countItems(genre) }}件</span>
          </button>
        </nav>
      </div>
    </header>

    <main class="container main">
      <p v-if="loading" class="message">
        読み込み中…（朝いちばんは1〜2分かかることがあります）
      </p>
      <p v-else-if="errorMessage" class="message error">{{ errorMessage }}</p>

      <div v-else-if="activeGenre" class="cards">
        <section v-for="source in activeGenre.sources" :key="source.name" class="card">
          <div class="card-header">
            <h2 class="card-title">
              {{ source.name }}<span class="feed-name">｜{{ source.feedName }}</span>
            </h2>
            <span class="basis">{{ source.basis }}</span>
          </div>

          <p v-if="source.error" class="card-message error">{{ source.error }}</p>

          <ol class="items">
            <li v-for="(item, index) in source.items" :key="item.link" class="item">
              <span class="rank">{{ index + 1 }}</span>
              <div class="item-body">
                <a
                  class="item-title"
                  :href="item.link"
                  :title="item.title"
                  target="_blank"
                  rel="noopener"
                >
                  {{ item.title }}
                </a>
                <div class="item-meta">
                  <span>{{ item.siteName }}</span>
                  <span v-if="item.publishedAt">{{ formatDateTime(item.publishedAt, false) }}</span>
                  <span v-if="item.bookmarkCount != null" class="users">
                    {{ item.bookmarkCount }} users
                  </span>
                </div>
              </div>
            </li>
          </ol>

          <p v-if="source.note" class="card-message">{{ source.note }}</p>
        </section>
      </div>

      <p v-if="news" class="footer-note">
        記事タイトルをクリックすると、元のサイトが新しいタブで開きます。
      </p>
    </main>
  </div>
</template>

<style>
/* ページ全体に効かせる設定（scoped なし） */
body {
  margin: 0;
  background: #f4f5f7;
  color: #1a1d23;
  font-family: 'Hiragino Sans', 'Yu Gothic UI', 'Meiryo', system-ui, sans-serif;
}
</style>

<style scoped>
.container {
  max-width: 1120px;
  margin: 0 auto;
  padding-left: 24px;
  padding-right: 24px;
}

/* ヘッダー */
.header {
  background: #fff;
  border-bottom: 1px solid #e1e4e9;
}

.header-inner {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding-top: 18px;
}

.header-top {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.title {
  margin: 0;
  font-size: 21px;
  font-weight: 800;
}

.subtitle {
  margin: 2px 0 0;
  font-size: 12px;
  color: #5b6170;
}

.header-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
}

.updated {
  font-size: 12px;
  color: #5b6170;
}

.refresh-error {
  font-size: 12px;
  color: #b42318;
}

.refresh-button {
  min-height: 44px;
  padding: 0 18px;
  border: 0;
  border-radius: 10px;
  background: #2563eb;
  color: #fff;
  font-family: inherit;
  font-size: 14px;
  font-weight: 700;
  cursor: pointer;
}

.refresh-button:disabled {
  opacity: 0.6;
  cursor: default;
}

/* タブ */
.tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.tab {
  display: flex;
  align-items: center;
  gap: 8px;
  min-height: 44px;
  margin-bottom: -1px;
  padding: 0 16px;
  border: 0;
  border-bottom: 3px solid transparent;
  background: transparent;
  color: #5b6170;
  font-family: inherit;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
}

.tab.active {
  border-bottom-color: #2563eb;
  color: #1a1d23;
  font-weight: 700;
}

.tab-count {
  padding: 2px 8px;
  border-radius: 999px;
  background: #f1f3f6;
  color: #5b6170;
  font-size: 11px;
  font-weight: 500;
}

/* 本文 */
.main {
  padding-top: 24px;
  padding-bottom: 40px;
}

.message {
  font-size: 14px;
  color: #5b6170;
}

.error {
  color: #b42318;
}

.cards {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(min(100%, 460px), 1fr));
  gap: 20px;
  align-items: start;
}

/* カード */
.card {
  padding: 18px 22px 10px;
  border: 1px solid #e1e4e9;
  border-radius: 14px;
  background: #fff;
}

.card-header {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 8px 12px;
  margin-bottom: 6px;
}

.card-title {
  margin: 0;
  font-size: 16px;
  font-weight: 700;
}

.feed-name {
  font-weight: 500;
  color: #5b6170;
}

.basis {
  padding: 4px 10px;
  border-radius: 999px;
  background: #f1f3f6;
  color: #3a404c;
  font-size: 12px;
  white-space: nowrap;
}

.card-message {
  margin: 10px 0;
  font-size: 12px;
  color: #5b6170;
}

/* 記事 */
.items {
  margin: 0;
  padding: 0;
  list-style: none;
}

.item {
  display: flex;
  align-items: flex-start;
  gap: 14px;
  padding: 14px 0;
  border-top: 1px solid #eceef1;
}

.rank {
  display: flex;
  flex: none;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: 8px;
  background: #f1f3f6;
  color: #3a404c;
  font-size: 13px;
  font-weight: 700;
}

.item-body {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
}

/* タイトルは常に2行分の高さ。3行以上は2行目の最後を「…」で省略 */
.item-title {
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  line-clamp: 2;
  overflow: hidden;
  min-height: calc(1.55em * 2);
  color: #1a1d23;
  font-size: 15px;
  font-weight: 600;
  line-height: 1.55;
  text-decoration: none;
}

.item-title:hover {
  color: #1d4ed8;
  text-decoration: underline;
}

.item-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 12px;
  font-size: 12px;
  color: #5b6170;
}

.users {
  color: #2563eb;
  font-weight: 700;
}

.footer-note {
  margin: 24px 0 0;
  font-size: 12px;
  color: #5b6170;
}
</style>