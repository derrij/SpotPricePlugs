---
layout: default
lang: pl
home: /pl/
blog: /pl/blog/
title: Blog
description: "Praktyczne poradniki o cenach Nord Pool, inteligentnych gniazdkach i realnych oszczędnościach."
permalink: /pl/blog/
---

# Blog

Praktyczne poradniki o cenach Nord Pool, inteligentnych gniazdkach i realnych oszczędnościach.

{% assign loc = site.posts | where: "lang", "pl" %}
{% for post in loc %}
<div class="card">
  <a href="{{ site.baseurl }}{{ post.url }}"><strong>{{ post.title }}</strong></a>
  <p class="post-meta">{{ post.date | date: "%Y-%m-%d" }}</p>
  <p>{{ post.excerpt }}</p>
</div>
{% endfor %}
{% if page.lang != 'en' %}
<h2>Więcej poradników (po angielsku)</h2>
{% assign enposts = site.posts | where: "lang", "en" %}
{% for post in enposts %}
<div class="card">
  <a href="{{ site.baseurl }}{{ post.url }}"><strong>{{ post.title }}</strong></a>
  <p class="post-meta">{{ post.date | date: "%Y-%m-%d" }} · EN</p>
  <p>{{ post.excerpt }}</p>
</div>
{% endfor %}
{% endif %}
