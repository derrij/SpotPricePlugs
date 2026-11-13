---
layout: default
lang: ru
home: /ru/
blog: /ru/blog/
title: Блог
description: "Практические руководства о ценах Nord Pool, умных розетках и реальной экономии."
permalink: /ru/blog/
---

# Блог

Практические руководства о ценах Nord Pool, умных розетках и реальной экономии.

{% assign loc = site.posts | where: "lang", "ru" %}
{% for post in loc %}
<div class="card">
  <a href="{{ site.baseurl }}{{ post.url }}"><strong>{{ post.title }}</strong></a>
  <p class="post-meta">{{ post.date | date: "%Y-%m-%d" }}</p>
  <p>{{ post.excerpt }}</p>
</div>
{% endfor %}
{% if page.lang != 'en' %}
<h2>Больше руководств (на английском)</h2>
{% assign enposts = site.posts | where: "lang", "en" %}
{% for post in enposts %}
<div class="card">
  <a href="{{ site.baseurl }}{{ post.url }}"><strong>{{ post.title }}</strong></a>
  <p class="post-meta">{{ post.date | date: "%Y-%m-%d" }} · EN</p>
  <p>{{ post.excerpt }}</p>
</div>
{% endfor %}
{% endif %}
